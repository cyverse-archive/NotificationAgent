#!/usr/local/bin/node

const fs = require('fs');
const daemon = require('daemon.node');
const http = require('http');
const listener = require('./listener');
const path = require('path');
const sys = require('sys');
const fix_state = require('./fix_inconsistent_state');

const job_status_handler = require('./job_status_handler');
const message_deletion_handler = require('./message_deletion_handler');
const message_query_handler = require('./message_query_handler');
const output_folder_handler = require('./output_folder_handler');

/*
 * Creates a directory and its ancestors if necessary.
 *
 * params:
 *   dirpath - the path to the directory to create.
 *   mode    - the mode of any newly created directories.
 */
var mkdir_p = function (dirpath, mode) {
    if (!path.existsSync(dirpath)) {
        mkdir_p(path.dirname(dirpath), mode);
        fs.mkdirSync(dirpath, mode);
    }
};

/*
 * Creates a closure containing the main application.
 *
 * returns:
 *     an object with all of the mode methods defined.
 */
var service = function() {
    this.server = null;

    const ARG_LENGTH = 4;  // The number of arguments we expect.
    const POS_PROG = 1;    // The position of the program name.
    const POS_CONF = 2;    // The position of the configuration file name.
    const POS_PID = 3;     // The position of the pid file name.

    this.outputfs = null;
    
    this.get_logging_stream = function () {
        return fs.createWriteStream(config.logfile, {'flags' : 'a'});
    }
    
    this.setup_logging = function () {
        var self = this;
        self.outputfs = get_logging_stream();

        console.log = function(msg) {
            var now = new Date();
            self.outputfs.write("[" + now.toString() + "] " + msg + "\n");
        }
    }

    this.get_log_stream = function () {
        return this.outputfs;
    }

    this.restart_log = function () {
        var log_stream = get_log_stream();
        log_stream.end();
        setup_logging();
    }

    /*
     * Runs the server.
     */
    this.run = function() {
        fix_state.resolve_conflicts(config.osm_base_url, config.jobs_bucket, config.notifications_bucket);
        
        var handlers = [
            job_status_handler.create({
                "forward_urls": config.msg_forward_urls,
                "osm_base_url": config.osm_base_url,
                "enable_email": config.enable_email,
                "email_config": config.email_config
            }),
            message_query_handler.create(config.osm_base_url),
            message_deletion_handler.create(config.osm_base_url),
            output_folder_handler.create(config.osm_base_url)
        ];
	
	// Output pid file
	fs.writeFile(process.argv[POS_PID], process.pid + "", function(err) {
	    if(err) {
		sys.puts(err);
		sys.exit(1);
	    }
	});

        server = listener.create(config.listen_port, handlers);
        server.run();
    }

    /*
     * Loads the configuration file.
     */
    this.load_config_file = function(filename) {
        try {
            var config_file = fs.realpathSync(process.argv[POS_CONF]);
            var config_text = fs.readFileSync(config_file);
            return JSON.parse(config_text);
        }
        catch (err) {
            sys.puts("unable to load the configuration file: " + err);
            process.exit(1);
        }
    }

    // Load the configuration file.
    this.config = load_config_file(process.argv[POS_CONF]);

    return this;
}();

function write_pid() {
    var pid_file = process.argv[3];
    console.log("Writing PID " + process.pid + " to " + pid_file);
    fs.writeFileSync(pid_file, process.pid + "");
}

process.on('SIGUSR1', function () {
    service.restart_log();
});

process.on('SIGTERM', function () {
    console.log("SIGTERM signal received, stopping.");
    process.exit(1);
});

process.on('SIGINT', function() {
    console.log("SIGINT signal received, stopping.");
    process.exit(1);
});

process.on('SIGQUIT', function() {
    console.log("SIGQUIT signal received, stopping.");
    process.exit(1);
})

write_pid();
service.setup_logging();
service.run();
