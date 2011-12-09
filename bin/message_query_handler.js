const message_reformatter = require('./message_reformatter');
const osmclient = require('./osmclient');
const sys = require('sys');
const url = require('url');

/*
 * Creates a new message query handler.
 *
 * params:
 *   osm_base_url - the base URL used to communicate with the OSM.
 */
exports.create = function(osm_base_url) {

    // The beginning of the error message for failure to handle a message.
    const HANDLE_ERR = "unable to handle ";

    /*
     * Gets only messages that haven't been seen yet.  This is functionally
     * the same as calling get_messages() with the "seen" flag equal to
     * false.
     *
     * params:
     *   res   - the response object.
     *   query - the query that was sent in the body of the request.
     */
    var get_unseen_messages = function(res, query) {
        query.seen = false;
        get_messages(res, query);
    }

    /*
     * Forwards a lookup request to the OSM.
     *
     * params:
     *   res   - the response object.
     *   query - the query that was sent in the body of the request.
     */
    var get_messages = function(res, query) {
        console.log("received query for messages: "
            + JSON.stringify(query))
        var limit = extract_limit(query);
        perform_query(res, format_query(query), limit);
    };

    /*
     * Extracts the response limit from the query that was sent in the body
     * of the request.  If no limit was specified or the limit can't be parsed
     * as an integer then all results will be returned.
     *
     * params:
     *   orig_query - the query that was sent in the body of the request.
     * returns:
     *   the limit or Infinity if there is no limit.
     */
    var extract_limit = function(orig_query) {
        var limit = Infinity;
        if (typeof(orig_query.limit) !== 'undefined') {
            var new_limit = parseInt(orig_query.limit);
            if (!isNaN(new_limit)) {
                limit = new_limit;
                console.log("limiting response to " + limit + " messages");
            }
        }
        return limit;
    };

    /*
     * Formats the query to forward to the OSM.
     *
     * params:
     *   orig_query - the query that was sent in the body of the request.
     * returns:
     *   the query to forward to the OSM.
     */
    var format_query = function(orig_query) {
        console.log("formatting the OSM query");
        var query = {};
        query['state.user'] = orig_query.user;
        query['state.deleted'] = false;
        if (typeof(orig_query.seen) !== undefined) {
            query['state.seen'] = orig_query.seen;
        }
        console.log("formatted OSM query: " + JSON.stringify(query));
        return query;
    };

    /*
     * Sends the query to the OSM clienta and returns the response to the
     * client.
     *
     * params:
     *   res   - the response object.
     *   query - the query to forward to the OSM.
     *   limit - the maximum number of matching messages to return.
     */
    var perform_query = function(res, query, limit) {
        console.log("sending the query to the OSM");
        var osm_client = osmclient.create(osm_base_url, 'notifications');
        osm_client.on('response', function(response) {
            console.log("received successful response from the OSM");
            var response_json = JSON.parse(response);
            var messages = extract_messages(response_json, limit);
            var result = {'messages': messages};
            res.writeHead(200, {'Content-Type': 'application/json'});
            res.end(JSON.stringify(result));
        });
        osm_client.on('request_failed', function(err, body) {
            console.log(HANDLE_ERR + body + ': ' + err);
            res.writeHead(500, {'Content-Type': 'text/plain'});
            res.end();
        });
        osm_client.search(JSON.stringify(query));
    };

    var deep_copy_message = function (msg_to_copy) {
        var msg_json = JSON.stringify(msg_to_copy);
        var new_msg = JSON.parse(msg_json);
        return new_msg;
    };

    /*
     * Extracts the messages from the results that were returned by the OSM.
     *
     * params:
     *   results - the results returned by the OSM.
     *   limit   - the maximum number of messages to return to the client.
     * returns:
     *   the extracted messages.
     */
    var extract_messages = function(results, limit) {
        console.log("extracting messages from OSM response");
        var reformatter = message_reformatter.create();
        var messages = [];

        var objects = results.objects.sort(function (a, b) {
            a_date = Date.parse(a.state.message.timestamp);
            b_date = Date.parse(b.state.message.timestamp);
            return a_date - b_date;
        });
        var start = Math.max(0, objects.length - limit);

        for (var i = start; i < objects.length; i++) {
            var message = objects[i].state;
            var uuid = objects[i].object_persistence_uuid;
            message.message.id = uuid;
            update_seen_flag(uuid, message);

            var msg_copy = deep_copy_message(message);
            delete msg_copy.seen;
            messages.push(reformatter.reformat(msg_copy));
        }

        return messages;
    };

    /*
     * Updates the message seen flag in the database.
     *
     * params:
     *   uuid    - the UUID of the message to update.
     *   message - the message to update.
     */
    var update_seen_flag = function(uuid, update_message) {
        //Copy the reference in 'update_message' to the local 'message' var,
        //which should resolve any synchronization issues caused by the for-loop
        //in extract_messages().
        var message = update_message;

        console.log("marking message " + uuid + " as seen");

        if (!message.seen) {
            message.seen = true;

            var osm_client = osmclient.create(osm_base_url, 'notifications');
            osm_client.on('request_failed', function(err, text) {
               console.log(err);
            });

            osm_client.update_field(uuid, 'seen', message.seen);
            //osm_client.update(uuid, message);
        }
    };

    // The subroutines used to handle all of the paths that we can handle.
    var HANDLER_FOR = {
        '/get-unseen-messages': get_unseen_messages,
        '/get-messages': get_messages
    };

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
            var handler = HANDLER_FOR[req.url];
            return req.method === 'POST' && typeof(handler) !== 'undefined';
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

            console.log("handling a message query request");

            // Set the request up to fetch the message body asynchronously.
            var body = '';
            req.on('data', function(chunk) {
                body += chunk;
            });
            req.on('end', function() {
                try {
                    var query = JSON.parse(body);
                    var handler = HANDLER_FOR[req.url];
                    handler(res, query);
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
