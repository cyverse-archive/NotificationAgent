const osmclient = require('./osmclient');
const sys = require('sys');
const url = require('url');

/*
 * Creates a new message deletion handler.
 *
 * params:
 *   osm_base_url - the base URL to use to connect to the OSM.
 * returns:
 *   the new message deletion handler.
 */
exports.create = function(osm_base_url) {

    // The beginning of the error message for failure to handle a message.
    const HANDLE_ERR = "unable to handle ";

    /*
     * Marks a message as deleted in the OSM.
     *
     * params:
     *   res     - the response object.
     *   uuids   - the message UUIDs.
     *   index   - the index of the message to delete.
     *   message - the message to mark as deleted.
     */
    var delete_message = function(res, uuids, index, message) {
        var uuid = uuids[index];
        console.log("marking notification " + uuid + " as deleted");
        if (!message.deleted) {
            var osm_client = osmclient.create(osm_base_url, 'notifications');
            osm_client.on('response', function(response) {
                retrieve_and_delete_message(res, uuids, index + 1);
            });
            osm_client.on('request_failed', function(err, body) {
                res.writeHead(500, {'Content-Type': 'text/plain'});
                res.end(err);
            });
            message.deleted = true;
            osm_client.update(uuid, message);
        }
        else {
            retrieve_and_delete_message(res, uuids, index + 1);
        }
    }

    /*
     * Gets a message to delete from the OSM.
     *
     * params:
     *   res   - the response object.
     *   uuids - the message UUIDs.
     *   index - the index of the message to delete.
     */
    var get_message_to_delete = function(res, uuids, index) {
        var uuid = uuids[index];
        console.log("retrieving notification " + uuid + " for deletion");
        var query = {'object_persistence_uuid': uuid};
        var osm_client = osmclient.create(osm_base_url, 'notifications');
        osm_client.on('response', function(response) {
            var objects = JSON.parse(response).objects;
            if (objects.length >= 1) {
                delete_message(res, uuids, index, objects[0].state);
            }
            else {
                var msg = "attempt to delete non-existent message " + uuid
                    + " ignored";
                console.log(msg);
                retrieve_and_delete_message(res, uuids, index + 1);
            }
        });
        osm_client.on('request_failed', function(err, body) {
            res.writeHead(500, {'Content-Type': 'text/plain'});
            res.end(err);
        });
        osm_client.search(JSON.stringify(query));
    }

    /*
     * Retrieves and deletes a message in the OSM.
     *
     * params:
     *   res   - the response object.
     *   uuids - the UUIDs of the messages to delete.
     *   index - the index of the message to delete.
     */
    var retrieve_and_delete_message = function(res, uuids, index) {
        if (index < uuids.length) {
            get_message_to_delete(res, uuids, index);
        }
        else {
            res.writeHead(200, {'Content-Type': 'text/plain'});
            res.end();
        }
    }

    /*
     * Marks several messages as deleted in the OSM.
     *
     * params:
     *   res   - the response object.
     *   uuids - the UUIDs of hte messages to delete.
     */
    var delete_all_messages = function(res, uuids) {
        retrieve_and_delete_message(res, uuids, 0);
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
            return req.method === 'POST' && req.url === '/delete';
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

            console.log("handling a message deletion request");

            // Set the request up to fetch the message body asynchronously.
            var body = '';
            req.on('data', function(chunk) {
                body += chunk;
            });
            req.on('end', function() {
                try {
                    delete_all_messages(res, JSON.parse(body).uuids);
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