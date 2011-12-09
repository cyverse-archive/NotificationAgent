const events = require('events');
const http = require('http');
const sys = require('sys');
const url = require('url');

/*
 * Creates a new output file registration client.
 *
 * params:
 *   registration_url - the URL of the service used to register the files.
 * returns:
 *   the new output file registration client.
 */
exports.create = function(registration_url) {
    var registration_client = Object.create(new events.EventEmitter, {});

    /*
     * Sends a registration request to the output file registration service.
     * An 'error' event is emitted if the connection can't be established.
     *
     * params:
     *   self - the registration client that is sending the request.
     *   body - the body of the registration request.
     * returns:
     *   the request object.
     */
    var send_registration_request = function(self, body) {
        var parsed_url = url.parse(registration_url);
        var port = parsed_url.port || 80;
        var host = parsed_url.hostname;
        var path = parsed_url.pathname || '/';
        var query = parsed_url.query || '';
        var hash = parsed_url.hash || '';
        console.log('port = ' + port);
        console.log('host = ' + host);
        console.log('path = ' + path);
        console.log('query = ' + query);
        console.log('hash = ' + hash);
        var client = http.createClient(port, host);
        client.on('error', function(err) {
            console.log(err);
            if (err instanceof Error) {
                console.log(err.stack);
            }
            self.emit('error', "unable to connect to " + registration_url);
        });
        var request = client.request('POST', path + query + hash, {
            'Host': host, 'Content-Type': 'application/json'
        });
        request.end(body, 'utf8');
        return request;
    };

    /*
     * Retrieves the body of the given HTTP response.  Once the response body
     * is retrieved, a 'response' event is emitted to notify any listeners of
     * the response.
     *
     * params:
     *   self - the registration client that made the request.
     *   body - the body of the registration request.
     */
    var get_response_body = function(self, res) {
        var body = '';
        res.on('data', function(chunk) {
            console.log("chunk: " + chunk);
            body += chunk;
        });
        res.on('end', function() {
            console.log(body);
            //self.emit('response', JSON.parse(body));
        });
    }

    /*
     * Registers the resource at the given URL in the workspace with the given
     * identifier.  If the resource referenced by the URL is a directory then
     * the contents of the directory are recursively registered.  Upon success,
     * a 'response' event containing an object representing the body of the
     * response is emitted.  If the resource can't be registered for any
     * reason, an 'error' event is emitted.
     *
     * params:
     *   workspace_id - the workspace identifier.
     *   resource_url - a URL referencing the resource to register.
     */
    registration_client.register = function(workspace_id, resource_url) {
        var self = this;
        var body = JSON.stringify({
            'workspaceId': workspace_id,
            'resourceUrl': resource_url
        });
        var request = send_registration_request(self, body);
        request.on('response', function(res) {
            if (res.statusCode === 200) {
                get_response_body(self, res);
            }
            else {
                var msg = 'unable to register output: ' + res.statusCode;
                self.emit('error', msg);
            }
        });
    }

    return registration_client;
}
