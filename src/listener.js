const http = require('http');
const sys = require('sys');

/*
 * Creates a new listener.
 *
 * params:
 *   port     - the port to listen to.
 *   handlers - an array of request handlers.
 * returns:
 *   the new listener.
 */
exports.create = function(port, handlers) {
    var server = {};

    /*
     * Creates and sends a response to a request that was not accepted by any
     * of the registered request handlers.
     */
    var unhandled_request = function(req, res) {
        res.writeHead(404, {'Content-Type': 'text/plain'});
        res.end("no " + req.method + " service found for " + req.url);
    };

    // Create the HTTP server that will handle all of the requests.
    var http_server = http.createServer(function(req, res) {
        for (var i = 0; i < handlers.length; i++) {
            var handler = handlers[i];
            if (handler.can_handle(req)) {
                handler.handle(req, res);
                return;
            }
        }
        unhandled_request(req, res);
    });

    /*
     * Runs the listener.
     */
    server.run = function() {
        console.log("listening for connections on port " + port);
        http_server.listen(port);
    };

    /*
     * Shuts the listener down.
     */
    server.shutdown = function() {
        http_server.close();
    };

    return server;
}
