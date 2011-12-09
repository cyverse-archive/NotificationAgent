const osmclient = require('./osmclient');
const sys = require('sys');
const url = require('url');

/*
 * Creates a new output folder handler
 *
 * params:
 *   osm_base_url - the base URL to use to connect to the OSM.
 * returns:
 *   the new output folder handler.
 */
exports.create = function(osm_base_url) {

    // The beginning of the error message for failure to handle a message.
    const HANDLE_ERR = "unable to handle ";

    /*
     * Stores the folder ID in a state object in the OSM.
     *
     * params:
     *   res       - the response object.
     *   results   - the array of results from the OSM.
     *   folder_id - the output folder ID.
     *   index     - the index of the result object to update.
     */
    var store_folder_id = function(res, results, folder_id, index) {
        if (index < results.length) {
            console.log("storing the folder ID for job at index " + index);
            var osm_client = osmclient.create(osm_base_url, 'jobs');
            osm_client.on('response', function(response) {
                console.log("folder ID successfully stored for job at index "
                    + index);
                store_folder_id(res, results, folder_id, index + 1);
            });
            osm_client.on('request_failed', function(err, body) {
                console.log("unable to store the folder id for job at index "
                    + index);
                res.writeHead(500, {'Content-Type': 'text/plain'});
                res.end(err);
            });
            var uuid = results[index].object_persistence_uuid;
            var state = results[index].state;
            state.output_folder_id = folder_id;
            osm_client.update(uuid, state);
        }
        else {
            console.log("folder ID successfully stored in all matching jobs");
            res.writeHead(200, {'Content-Type': 'text/plain'});
            res.end();
        }
    }

    /*
     * Gets the state information for the job with the given UUID.
     *
     * params:
     *   res       - the response object.
     *   uuid      - the job UUID.
     *   folder_id - the folder identifier.
     */
    var get_state_information = function(res, uuid, folder_id) {
        console.log("getting the state information for job " + uuid);
        var osm_client = osmclient.create(osm_base_url, 'jobs');
        osm_client.on('response', function(response) {
            console.log("state informaiton for job " + uuid + " retrieved");
            var results = JSON.parse(response).objects;
            store_folder_id(res, results, folder_id, 0);
        });
        osm_client.on('request_failed', function(err, body) {
            res.writeHead(500, {'Content-Type': 'text/plain'});
            res.end(err);
        });
        var query = {'state.uuid': uuid};
        osm_client.search(JSON.stringify(query));
    }

    /*
     * Retrieves the value of a required field from the given object.
     *
     * params:
     *   json - the object to get the field value from.
     *   name - the field name.
     */
    var get_required_field = function(json, name) {
        var value = json[name];
        if (typeof(value) === 'undefined' || value === null) {
            throw 'missing required field: ' + name;
        }
        return value;
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
            return req.method === 'POST' && req.url === '/save-output-folder';
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

            console.log("handling a folder ID storage request");

            // Set the request up to fetch the message body asynchronously.
            var body = '';
            req.on('data', function(chunk) {
                body += chunk;
            });
            req.on('end', function() {
                try {
                    var json = JSON.parse(body);
                    var uuid = get_required_field(json, 'uuid');
                    var folder_id = get_required_field(json, 'folderId');
                    get_state_information(res, uuid, folder_id);
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
