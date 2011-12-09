const events = require('events');
const http = require('http');
const url = require('url');

/*
 * Creates a new OSM client.
 *
 * params:
 *   osm_base_url - the base URL used to connect to the OSM.
 *   bucket       - the OSM bucket to use.
 * returns:
 *   the OSM client.
 */
exports.create = function(osm_base_url, bucket) {
    var self = Object.create(new events.EventEmitter, {});

    /*
     * Creates a request to a given URL.
     *
     * params:
     *     a_url - the URL.
     *     body  - the message body.
     * returns:
     *     the client request.
     */
    self.send_request = function(a_url, body) {
        console.log("sending request " + body + " to OSM");
        var parsed_url = url.parse(a_url);
        var port = parsed_url.port || 80;
        var host = parsed_url.hostname;
        var path = parsed_url.pathname || '/';
        var query = parsed_url.query || '';
        var hash = parsed_url.hash || '';
        var osm = http.createClient(port, host);
        var self = this;
        osm.on('error', function(err) {
            var msg = 'unable to connect to OSM at ' + a_url + ': ' + err;
            self.emit('request_failed', msg, body);
        });
        var request = osm.request('POST', path + query + hash,
            {'Host': host, 'Content-Type': 'text/plain'});
        request.end(body, 'utf8');
        return request;
    };

    /*
     * Obtains an HTTP response body.
     *
     * params:
     *     res - the response object.
     */
    self.get_response_body = function(res) {
        console.log("getting the body of the response from the OSM");
        var self = this;
        var body = '';
        res.on('data', function(chunk) {
            body += chunk;
        });
        res.on('end', function() {
            var response = body.replace(/^\s+|\s+$/g, '');
            self.emit('response', response);
        });
    };

    /*
     * Sends a request to the OSM and retrieves the response.
     *
     * params:
     *   a_url     - the URL to send the request to.
     *   text      - the request text.
     *   error_msg - the error message to use if the request fails.
     */
    self.perform_request = function(a_url, text, error_msg) {
        console.log("sending OSM request");
        var request = this.send_request(a_url, text);
        var self = this;
        request.on('response', function(res) {
            if (res.statusCode === 200) {
                self.get_response_body(res);
            }
            else {
                var msg = error_msg + ': ' + res.statusCode;
                self.emit('request_failed', msg, text);
            }
        });
    };

    /*
     * Saves an object in the OSM.
     *
     * params:
     *     text - the message text.
     */
    self.save = function(text) {
        console.log("saving an OSM object");
        var persist_url = osm_base_url + '/' + bucket;
        var error_msg = 'unable to save object';
        this.perform_request(persist_url, text, error_msg);
    };

    /*
     * Searches for objects in the OSM.
     *
     * params:
     *   query - the query text.
     */
    self.search = function(text) {
        console.log("searching for objects in the OSM");
        var search_url = osm_base_url + '/' + bucket + '/query';
        var error_msg = 'query failed';
        this.perform_request(search_url, text, error_msg);
    };

    /*
     * Updates an object in the OSM.
     *
     * params:
     *   uuid   - the UUID of the object to update.
     *   object - the updated object.
     */
    self.update = function(uuid, object) {
        console.log("updating the OSM");
        var update_url = osm_base_url + '/' + bucket + '/' + uuid;
        var error_msg = 'update failed';
        this.perform_request(update_url, JSON.stringify(object), error_msg);
    };
    
    self.update_field = function (uuid, field, value) {
        console.log("Updating " + field + " to " + value + " for object " + uuid);
        var update_url = osm_base_url + '/' + bucket + '/' + uuid;
        var error_msg = 'Update of field ' + field + ' to ' + value + ' on object ' + uuid + 'failed.';
        var update_field = "state." + field;
        var upobj = {};
        upobj[update_field] = value;
        var set_field = {"$set" : upobj};
        this.perform_request(update_url, JSON.stringify(set_field), error_msg);
    };

    return self;
}
