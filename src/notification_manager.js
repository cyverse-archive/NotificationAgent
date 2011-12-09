const events = require('events');
const http = require('http');
const message_reformatter = require('./message_reformatter');
const osmclient = require('./osmclient');
const sys = require('sys');
const url = require('url');

/*
 * Creates a notification manager, which is an object that can be used to
 * store notifications in the OSM and forward them to a list of recipients.
 *
 * params:
 *   osm_base_url - the base URL used to access the OSM.
 *   forward_urls - an array of URLs to forward messages to.
 * returns:
 *   the new notification manager.
 */
exports.create = function(osm_base_url, forward_urls) {
    var manager = Object.create(new events.EventEmitter, {});

    // The beginning of the error message for failure to forward a message.
    const FORWARD_ERR = "unable to forward ";

    // The beginning of the error message for failure to handle a message.
    const HANDLE_ERR = "unable to handle ";

    /*
     * Forwards a message to all of the message recipients.
     *
     * params:
     *     body - the message body.
     *     urls - the list of URLs to forward the message to.
     */
    var forward_message_to_all = function(body, urls) {
        console.log("forwarding message to " + urls.length + " recipients");
        for (var i = 0; i < urls.length; i++) {
            forward_message_to_one(body, urls[i]);
        }
    };

    /*
     * Forwards a message to one of the message recipients.
     *
     * params:
     *     body  - the message body.
     *     a_url - the URL to forward the message to.
     */
    var forward_message_to_one = function(body, a_url) {
        var request = send_request(a_url, body);
        request.on('response', function(res) {
            if (res.statusCode !== 200) {
                console.log(FORWARD_ERR + body + ' to ' + a_url);
            }
        });
    };

    /*
     * Creates a request to a given URL.
     *
     * params:
     *     a_url - the URL.
     *     body  - the message body.
     * returns:
     *     the client request.
     */
    var send_request = function(a_url, body) {
        console.log("forwarding message to " + a_url);
        var parsed_url = url.parse(a_url);
        var port = parsed_url.port || 80;
        var host = parsed_url.hostname;
        var path = parsed_url.pathname || '/';
        var query = parsed_url.query || '';
        var hash = parsed_url.hash || '';
        var dest_client = http.createClient(port, host)
        dest_client.on('error', function(err) {
            console.log(FORWARD_ERR + body + ' to ' + a_url);
        });
        var request = dest_client.request('POST', path + query + hash, {
            'Host': host, 'Content-Type': 'text/plain'
        });
        request.end(body, 'utf8');
        return request;
    };

    /*
     * Stores a single message in the OSM and forwards it to each of the URLs
     * that notifications are supposed to be forwarded to.
     *
     * params:
     *   self     - the notification manager.
     *   messages - the array of messages to handle.
     *   index    - the index of the next message to handle.
     */
    var handle_message = function(self, messages, index) {
        console.log("persisting and sending notification at index " + index);
        var reformatter = message_reformatter.create();
        if (index < messages.length) {
            var json = messages[index];
            var osm_client = osmclient.create(osm_base_url, 'notifications');
            osm_client.on('response', function(uuid) {
                console.log("message at index " + index
                    + " successfully stored");
                json.message.id = uuid;
                reformatter.reformat(json);
                forward_message_to_all(JSON.stringify(json), forward_urls);
                handle_message(self, messages, index + 1);
            });
            osm_client.on('request_failed', function(err, body) {
                var error_msg = HANDLE_ERR + body + ': ' + err;
                console.log(error_msg);
                self.emit('error', error_msg);
            });
            osm_client.save(JSON.stringify(messages[index]));
        }
        else {
            self.emit('finished');
        }
    }

    /*
     * Stores all of the given messages in the OSM and forwards them to each
     * of the URLs that notifications are supposed to be forwarded to.  After
     * all of the messages have been persisted and forwarded, a 'finished'
     * event is emitted.  If any of the messages can't be stored in the OSM,
     * an 'error' event is emitted.  Failure to forward notifications isn't
     * considered to be as bad of an error because the DE can always retrieve
     * the notification later.
     *
     * params:
     *   messages - the array of messages to handle.
     */
    manager.handle_messages = function(messages) {
        handle_message(this, messages, 0);
    };

    return manager;
};
