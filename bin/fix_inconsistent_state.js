const http = require('http');
const events = require('events');
const sys = require('sys');
const url = require('url');

var create_osm_client = function (osm_baseurl, job_bucket, notifications_bucket) {
    var client = Object.create(new events.EventEmitter, {});

    var url_parts = url.parse(osm_baseurl);
    var osm_port = url_parts.port,
        osm_hostname = url_parts.hostname,
        osm_path = url_parts.pathname.replace(/\/$/, "");

    client.osm_path = osm_path;
    client.osm = http.createClient(osm_port, osm_hostname);
    client.osm.on("error", function (err) {
        console.log("unable to connect to the OSM at " + osm_baseurl + ": " + err);
    });

    client.query_jobs = function (query) {
        var self = this;

        var json_query = JSON.stringify(query);
        console.log("Job query " + json_query);

        var request = client.osm.request('POST', self.osm_path + '/' + job_bucket + '/query', {
            'host' : osm_hostname
        });

        request.end(json_query);

        request.on('response', function (response) {
            var data = "";

            response.on('data', function (chunk) {
                data += chunk.toString();
            });

            response.on('end', function () {
                self.emit('jobQueryDone', JSON.parse(data));
            });
        });
    };

    client.query_notifications = function (query) {
        console.log("Querying notifications.");
        var self = this;

        var json_query = JSON.stringify(query);
        console.log("Notification query: " + json_query);

        var request = client.osm.request('POST', self.osm_path + '/' + notifications_bucket + '/query', {
            'host' : osm_hostname,
            'Content-Type' : 'text/plain',
            'Content-Length' : json_query.length
        });

        request.end(json_query);

        request.on('response', function (response) {
            var data = "";

            response.on('data', function (chunk) {
                data += chunk.toString();
            });

            response.on('end', function () {
                self.emit('notificationQueryDone', JSON.parse(data));
            })
        });
    };

    client.update_notification = function (updated_notif, notif_uuid) {
        var self = this;

        var json_notif = JSON.stringify(updated_notif);

        var request = client.osm.request('POST', self.osm_path + '/' + notifications_bucket + '/' + notif_uuid, {
            'host' : osm_hostname
        });

        request.end(json_notif);

        request.on('response', function (response) {
            var data = "";

            response.on('data', function (chunk) {
                data += chunk.toString();
            });

            response.on('end', function (chunk) {
                self.emit('updatedNotification', response.statusCode, notif_uuid);
            });
        });
    };

    return client;
};


exports.resolve_conflicts = function (osm_base_url, jobs_bucket, notifications_bucket) {
    var parsed_url = url.parse(osm_base_url);

    var osm = create_osm_client(osm_base_url, jobs_bucket, notifications_bucket);
    var analysis_tracker = {};

    osm.on('notificationQueryDone', function (notifs_obj) {
        var notifs = notifs_obj.objects;

        //Build list of analysis IDs so we can query them from the jobs bucket.
        var list_of_aids = [];

        for (var i = 0; i < notifs.length; i++) {
            var notif = notifs[i];

            if (notif.state.hasOwnProperty("payload")) {
                list_of_aids.push(notif.state.payload.id);
                console.log("Notification entry for " + notif.state.payload.id + " is Running. ");
                analysis_tracker[notif.state.payload.id] = notif;
            }
        }

        //Query jobs for all of the analysis IDs listed in the notifications bucket that are in
        //the running state.

        if (list_of_aids.length > 0) {
            var job_analysises = osm.query_jobs({"state.uuid" : {"$in" : list_of_aids}});
        }
    });

    osm.on('jobQueryDone', function (job_objs) {
        var jobs = job_objs.objects;

        for (var i = 0; i < jobs.length; i++) {
            var job = jobs[i];
            var notif = analysis_tracker[job.state.uuid];
            var notif_status = notif.state.payload.status;
            var job_status = job.state.status;

            console.log("Analysis " + job.state.uuid + " was grabbed out of jobs bucket in the OSM.");

            if (job_status !== notif_status) {
                console.log("Job status of " + job.state.uuid + " is " + job_status + " while notification status is " + notif_status + ". Fixing.");
                notif.state.payload.status = job_status;

                console.log("Posting updated notification state to the OSM.");
                osm.update_notification(notif.state, notif.object_persistence_uuid);
            } else {
                console.log("Statuses match for " + job.state.uuid + ". Nothing to see here, moving on.");
            }
        };
    });

    osm.on('updatedNotification', function (status_code, uuid) {
        console.log("Got a status of " + status_code + " from the OSM when updating the notification status of analysis " + uuid);
    });

    //Get list of notifications that are in the Running state, since they're the ones
    //that are most likely to be in the wrong state.
    var running_notifs = osm.query_notifications({"state.payload.status" : "Running"});
};
