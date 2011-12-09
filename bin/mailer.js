
const email = require('node_mailer/node_mailer');

/*
 * Creates a new mailer.
 *
 * params:
 *   from_address - the e-mail address we're sending messages from.
 *   display_name - the name to display.
 *   smtp_host    - the host name or IP address of the SMPT server.
 *   smtp_port    - the port number to use when contacting the SMPT server.
 * returns:
 *   the new mailer.
 */
exports.create = function(args) {
    var from = args.from_address;
    var display = args.display_name;
    var host = args.smtp_host;
    var port = args.smtp_port;
    var mailer = {};

    /*
     * Sends an e-mail message.
     *
     * params:
     *   to      - the e-mail address we're sending the message to.
     *   subject - the message subject line.
     *   body    - the message body.
     */
    mailer.send_message = function(to, subject, body) {
        console.log("sending e-mail notification to " + to);
        email.send({
            'host': host,
            'port': port,
            'to': to,
            'from': from,
            'subject': subject,
            'body': body,
        },
        function(err, result) {
            if (err) {
                console.log(err);
            }
        });
    };

    return mailer;
}
