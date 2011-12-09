const http = require('http');
const osmclient = require('./osmclient');
const mailer = require('./mailer');
const notification_manager = require('./notification_manager');
const sys = require('sys');
const url = require('url');

/*
 * Creates an object that can handle a message that is being posted to the
 * notification agent.
 *
 * params:
 *     forward_urls - the URLs to forward messages to.
 *     osm_base_url - the base URL to use when contacting the OSM.
 *     enable_email - true if e-mail notifications are enabled.
 *     email_config - the e-mail configuration.
 * returns:
 *     an object that is capable of handling the messages.
 */
exports.create = function(args) {
    var forward_urls = args.forward_urls;
    var osm_base_url = args.osm_base_url;
    var enable_email = args.enable_email;
    var email_config = args.email_config;

    // The beginning of the error message for failure to forward a message.
    const FORWARD_ERR = "unable to forward ";

    // The beginning of the error message for failure to handle a message.
    const HANDLE_ERR = "unable to handle ";

    /*
     * Persists and forwards a notification message.
     *
     * params:
     *     res  - the response object.
     *     json - the message JSON.
     */
    var persist_and_send_notification = function(res, json) {
        console.log("persisting a notification for job " + json.payload.id);
        var manager = notification_manager.create(osm_base_url, forward_urls);
        manager.handle_messages([json]);
        manager.on('finished', function() {
            res.writeHead(200, {'Content-Type': 'text/plain'});
            res.end();
        });
        manager.on('error', function(err) {
            res.writeHead(500, {'Content-Type': 'text/plain'});
            res.end(err);
        });
    }

    /*
     * Converts the job state object to a message that can be forwarded to
     * the DE.
     *
     * params:
     *   state - the JSON object representing the job state.
     *   text  - the message text
     * returns:
     *   a message relaying the job state information.
     */
    var state_to_message = function(state, text) {
        console.log("generating a notification for job " + state.uuid);
        var now = new Date();
        var output_dir_path = url.parse(state.output_dir).pathname;

        return {
            'type': 'analysis',
            'user': state.user,
            'deleted': false,
            'seen': false,
            'workspaceId': state.workspace_id,
            'outputDir': state.output_dir,
            'outputManifest' : state.output_manifest,
            'message': {
                'id': '',
                'timestamp': now.toString(),
                'text': text
            },
            'payload': {
                'id': state.uuid,
                'action': 'job_status_change',
                'status': state.status,
                'resultfolderid': output_dir_path,
                'user': state.user,
                'name': state.name || '',
                'startdate': state.submission_date || '',
                'enddate': state.completion_date || '',
                'analysis_id': state.analysis_id || '',
                'analysis_name': state.analysis_name || '',
                'description': state.description || ''
            }
        };
    };

    /*
     * Builds the notification message for the given state object.
     *
     * params:
     *   state - the job state object.
     * returns:
     *   the message text.
     */
    var build_message_text = function(state) {
        return 'job ' + state.name + ' ' + state.status.toLowerCase()
    }

    /*
     * Extracts the e-mail address from the job state object.
     *
     * params:
     *   state - the job state object.
     * returns:
     *   the e-mail address or null if one wasn't provided.
     */
    var extract_email_from_state = function(state) {
        console.log("extracting the e-mail address for job " + state.uuid);
        return typeof(state.email) !== 'undefined' ? state.email : null;
    }

    /*
     * Sends an e-mail notification to the e-mail address in the job state
     * object if an e-mail address is available and e-mail notificaitons are
     * enabled.
     *
     * params:
     *   state - the job state information.
     *   body  - the body of the e-mail message.
     */
    var send_email_notification = function(state) {
        var notify = state.notify || false;
        var email = extract_email_from_state(state);
        if (enable_email && notify && email !== null) {
            console.log("sending an e-mail notification for job "
                + state.uuid);

            var body = {
                "to" : email,
                "template" : email_config.template,
                "subject" : state.name + " status changed.",
                "values" : {
                    "analysisname" : state.name,
                    "analysisstatus" : state.status,
                    "analysisstartdate" : state.submission_date,
                    "analysisresultsfolder" : url.parse(state.output_dir).pathname,
                    "analysisdescription" : state.description
                }
            };

            var request_body = JSON.stringify(body);
            var parsed_url = url.parse(email_config.iplant_email_url);
            var port = parsed_url.port || 80;
            var host = parsed_url.hostname;
            var path = parsed_url.pathname || "/";
            var query = parsed_url.query || '';
            var hash = parsed_url.hash || '';

            var email_client = http.createClient(port, host);
            var self = this;

            email_client.on('error', function (err) {
                var msg = 'Unable to connect to iplant-email at ' + email_config.iplant_email_url + ': ' + err;
                self.emit('request_failed', msg, request_body);
            });

            var request = email_client.request('POST', path + query + hash, {
                'Host' : host,
                'Content-Type' : "application/json"
            });
            request.end(request_body, 'utf8');
        }
    }

    /*
     * Determines whether or not the job completed since the last time we saw
     * a status update for it.
     *
     * params:
     *   state - the job state information.
     */
    var job_just_completed = function(state) {
        console.log("checking to see if job " + state.uuid
            + " just completed");
        var status = state.status;
        var completion_date = state.completion_date || null;
        return status.match(/\s*(Completed|Failed)\s*/i)
            && completion_date === null;
    }

    /*
     * Determines whether or not the job status changed.  This function
     * assumes that the current status is always stored in state.status
     * and that the previous status is stored in state.previous_status.
     * The previous status will not be present when the job is first
     * created, so we assume that the status has changed if the previous
     * status isn't defined.
     *
     * params:
     *   state - the job state information.
     * returns:
     *   true if the job status has changed.
     */
    var job_status_changed = function(state) {
        console.log("checking to see if the status of job " + state.uuid
            + " changed");
        var curr_status = state.status;
        var prev_status = state.previous_status || "";
        console.log("current status: " + curr_status);
        console.log("previous status: " + prev_status);
        return curr_status !== prev_status;
    }

    /*
     * Updates the job state information in the OSM.  This includes any
     * changes that have been made to the state before this function was
     * called as well as updating the previous state to match the current
     * state.
     *
     * params:
     *   uuid  - the object persistence UUID for the job.
     *   state - the job state information.
     */
    var update_job_state = function(uuid, state) {
        console.log("updating the job state for job " + state.uuid);
        var osm_client = osmclient.create(osm_base_url, "jobs");
        osm_client.on("request_failed", function(err, body) {
            var msg = "unable to update the state information for job "
                + uuid + " in the OSM: " + err;
            console.log(msg);
        });
        state.previous_status = state.status;
        osm_client.update(uuid, state);
    }

    return {

        /*
         * Determines whether or not this is the right handler for the given
         * request.
         *
         * params:
         *     req - the request.
         * returns:
         *     true if this handler can handle the request.
         */
        can_handle : function(req) {
            return req.method === 'POST' && req.url === '/job-status';
        },

        /*
         * Handles the given request.  The assumption is that if this method
         * is being called then the caller has already verified that this
         * handler can handle the request by calling can_handle().  If this
         * turns out not to be the case then an exception is thrown.
         *
         * params:
         *     req - the request.
         *     res - the response.
         */
        handle : function(req, res) {
            if (!this.can_handle(req)) {
                throw "unable to handle the given request."
            }

            console.log("handling a job status update");

            // Set the request up to fetch the message body asynchronously.
            var body = '';
            req.on('data', function(chunk) {
                body += chunk;
            });
            req.on('end', function() {
                console.log("received job status update body");
                try {
                    var json = JSON.parse(body);
                    var uuid = json.object_persistence_uuid;
                    var state = json.state;
                    if (job_status_changed(state)) {
                        var message_text = build_message_text(state);

                        if (job_just_completed(state)) {
                            state.completion_date = new Date().toString();   
                        }

                        send_email_notification(state);

                        var message = state_to_message(state, message_text);
                        persist_and_send_notification(res, message);
                        update_job_state(uuid, state);
                    }
                    else {
                        res.writeHead(200, {'Content-Type': 'text/plain'});
                        res.end();
                    }
                }
                catch (err) {
                    console.log(HANDLE_ERR + body + ": " + err);
                    if (err instanceof Error) {
                        console.log(err.stack);
                    }
                    res.writeHead(400, {'Content-Type': 'text/plain'});
                    res.end(err.toString());
                }
            });
        }
    };
}
