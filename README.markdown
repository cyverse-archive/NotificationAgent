# Notification Agent
 
This service accepts callback notifications from the OSM about job status
changes and provides an endpoint for the Discovery Environment that is used to
look up the states of a user's analyses.  It also triggers the emails that are
sent when the state of an analysis changes.

## Overview

The notification agent provides one endpoint that is designed to accept
incoming job status updates from the OSM and three endpoints that are designed
to accept requests from the Discovery Environment.  The endpoint that accepts
updates from the OSM is `/job-status`.  This endpoint accepts updates in the
same format that is used by the JEX and Panopticon to store job status
information in the OSM:

```json
{
    "analysis_name" : "Some analysis name",
    "completion_date" : "Tue Jan 31 2012 08:58:40 GMT-0700 (MST)",
    "analysis_description" : "Some analysis description",
    "status" : "status-name",
    "output_dir" : "/path/to/job/output/directory/",
    "uuid" : "job-identifier",
    "condor-log-dir" : "/path/to/directory/containing/condor/log/files/",
    "working_dir" : "/path/to/job/working/directory/",
    "email" : "someuser@somewhere.com",
    "jobs" : {
       "step-name" : {
          "stderr" : "/path/to/directory/containing/condor/log/files/step-name-stderr",
          "log-file" : "/path/to/directory/containing/condor/log/files/step-name-log",
          "status" : "status-name",
          "exit-code" : "0",
          "environment" : "environment-variable-settings",
          "exit-by-signal" : "true-if-process-was-killed",
          "executable" : "/path/to/executable",
          "arguments" : "command-line-arguments",
          "id" : "step-name",
          "stdout" : "/path/to/directory/containing/condor/log/files/step-name-stdout"
       },
       ...
    },
    "request_type" : "request-type-name",
    "execution_target" : "execution-target-name",
    "user" : "someuser",
    "dag_id" : "directed-acyclic-graph-identifier",
    "notify" : "true-if-user-wants-notification-emails",
    "submission_date" : "submission-date-as-milliseconds-since-the-epoch",
    "workspace_id" : "numeric-workspace-identifier",
    "name" : "job-name",
    "description" : "",
    "now_date" : "timestamp-of-most-recent-update",
    "output_manifest" : ["list-of-output-files"],
    "nfs_base" : "/path/to/nfs/mountpoint/",
    "irods_base" : "/path/to/base/irods/directory",
    "analysis_id" : "analysis-identifier"
}
```

When a job status update is received, the notification agent first checks to
see if the overall job status has changed since the notification agent last
received an update for the job.  If the overall status hasn't changed then the
job status update is ignored.  Otherwise, the notification agent generates a
notification, stores it in the `notifications` bucket in the OSM, and forwards
the notification to any configured recipients.  (Note that the recipients are
not going to be used until server push is implemented.)  The notification is
always in this format:

```json
{
    "workspaceId" : "numeric-workspace-identifier",
    "deleted" : "true-if-the-notification-has-been-deleted",
    "message" : {
       "timestamp" : "notification-generation-timestamp",
       "text" : "notification-text",
       "id" : "notification-uuid"
    },
    "outputDir" : "/path/to/job/output/directory/",
    "seen" : true,
    "payload" : {
       "analysis_name" : "Some analysis name",
       "status" : "status-name",
       "name" : "job-name",
       "startdate" : "start-date-as-millisecons-since-the-epoch",
       "description" : "",
       "enddate" : "timestamp-of-job-completion",
       "resultfolderid" : "/path/to/job/output/directory/",
       "action" : "job_status_change",
       "user" : "someuser",
       "id" : "job-identifier",
       "analysis_id" : "analysis-identifier"
    },
    "user" : "someuser",
    "type" : "analysis",
    "outputManifest" : ["list-of-output-files"]
}
```

Once the notification is stored in the `notifications` bucket of the OSM, it
can be retrieved from the notification agent using the `/get-messages`
endpoint or the `/get-unseen-messages` endpoint.  These endpoints are roughly
equivalent except that the latter can only be used to list messages that
haven't been seen by the user yet.  The `/get-messages` endpoint takes a JSON
request body in this format:

```json
{
    "user": "username",
    "seen": "seen-flag",
    "limit": "limit"
}
```

The `user` field is the only field that is required in the query.  The `seen`
flag is optional.  If this field is omitted then both messages that have been
seen and messages that have not been seen will be included in the results.
The `limit` field is also optional.  If, for example, a limit of 50 is
specified then at most 50 messages will be returned by the query, with the
most recent notifications taking precedence.  If no limit is specified then
all of the messages that match the rest of the query parameters will be
returned in the results.

The `/get-unseen-messages` endpoint takes a JSON request body in the same
format as the `/get-messages` endpoint, but it's pointless to include the
`seen` flag in the request body (because this service always behaves as though
the `seen` flag were specified and set to `true`).

Both the `/get-messages` and `/get-unseen-messages` endpoints mark
notifications that they return as having been seen.  So that a query for
messages that have not been seen will never return a message that has been
returned by any endpoint.  This feature allows the DE to poll for notification
messages without having to worry about receiving the same notification message
more than once per session.

Notifications that the user no longer wishes to see may be marked as deleted
so no message query will ever return the message again.  Messages are deleted
using the `/delete` endpoint.  The `/delete` endpoint takes a JSON request
body in this format:

```json
{
    "uuids": [
        "some-uuid",
        "some-other-uuid",
        ...
    ]
}
```

This service will delete the messages corresponding to all of the identifiers
that are included in the request body.  An attempt to delete a message that
has already been marked as deleted is essentially a no-op, and will not cause
an error or warning message.  An attempt to delete a message that doesn't
exist will not cause an error, but it will cause a warning message to be
logged in the notification agent's log file.

## Notification Status Tracking

The notification agent keeps track of the status of each job that it sees,
which allows the notification agent to detect when the overall status of each
job changes.  This tracking is currently done in an in-memory map that
associates job identifiers with the most recent job status seen by the
notification agent.  This obviously poses some scalability problems, so a
future version of the notification agent will be modified to store the most
recent status of each job in a different way.  One possibility is to store the
most recent status for each job in a different bucket in the OSM.  Another
possibility is to store the state in a relational database.

## Startup Tasks

Because notifications are stored in memory, the notification agent needs to
load the most recent status that the notification agent has seen for each
job.  This is done by walking through all of the notifications that have been
generated (deleted or not) and building the in-memory map.  This will tend to
cause the notification agent start-up time to become slower as more and more
notifications are stored in the OSM.  This startup delay will be eliminated
when the notification status tracking mechanism is changed in the future.

If there is a configuration problem or the notification is down for an
extended period of time then it's possible for the status of a job to be
updated without the notification agent being aware of the change.  For this
reason, the notification agent scans the entire OSM upon startup, searching
for jobs for which the current job status doesn't match the status most
recently seen by the notification agent for that job.  One notification will
be generated for every job for which an inconsistent state is detected.

At the time of this writing, the job status tracking mechanism has been
changed for the notification agent in general, but not for the code that
generates notifications for jobs whose status has changed while the
notificaiton agent was down.  This can result in duplicate notificaitons under
some circumstances.  This problem will be fixed in an upcoming release.

## Installation and Configuration

The notification agent is packaged as an RPM and published in iPlant's YUM
repositories.  It can be installed using `yum install notificationagent` and
upgraded using `yum upgrade notificationagent`.

### Primary Configuration

The notification agent gets most of its configuration settings from Apache
Zookeeper.  These configuration settings are uploaded to Zookeeper using
Clavin, a command-line tool maintained by iPlant that allows configuration
properties and access control lists to be easily uploaded to Zookeeper.
Please see the Clavin documentation for information about how to upload
configuration settings.  Here's an example notification agent configuraiton
file:

```properties
# OSM configuration settings.
notificationagent.osm-base=http://by-tor:65535
notificationagent.osm-jobs-bucket=jobs
notificationagent.osm-notifications-bucket=notifications
notificationagent.osm-job-status-bucket=notificationagent_job_status

# E-mail configuration settings.
notificationagent.email-url=http://snow-dog:65534
notificationagent.enable-email=true
notificationagent.email-template=analysis_status_change

# Notification recipients.
notificationagent.recipients=
```

The OSM configuration settings tell the notification agent how to connect to
the OSM and which buckets to use.  The base URL is fairly self-explanatory.
The jobs bucket is the OSM bucket where the job status information is stored.
Similarly, the notifications bucket is the OSM bucket where the notifications
are stored.  The job status bucket is where the notification agent stores the
most recent status it has seen for each job.

The e-mail configuration settings are all fairly self-explanatory except for
the template, which is the name of the template that the e-mail service uses
when generating the message text.  In general, this setting will not change,
but it has been made configurable in case we need to support different
templates for different deployments.

The `notification.recipients` setting is a list of URLs to send notifications
to when a job status update is processed.  This feature is intended to be used
for server push, so this setting will probably be left blank until server push
is implemented or we find another use for this feature.

### Zookeeper Connection Information

One piece of information that can't be stored in Zookeeper is the information
required to connect to Zookeeper.  For the notification agent, this is stored
in a single file: `/etc/notificationagent/notificationagent.properties`.
Here's an example:

```properties
zookeeper=zookeeper://127.0.0.1:2181
```

After installing the notification agent, it will be necessary to modify this
file so that it points to teh correct host and port.

### Logging Settings

Since logging settings have to be changed fairly frequently for
troubleshooting, logging settings are not stored in Zookeeper.  Instead,
they're stored in a file on the local file system in
`/etc/notificationagent/log4j.properties'.  Since the notification agent uses
log4j, a lot of configuration settings are available.  See the [log4j
documentation](http://logging.apache.org/log4j/1.2/manual.html). for
detailed information about how to configure logging.

Even though complex logging configuration options are available, they're
normally not necessary because it's possible to exert a lot of control over
the logging behavior by simply tweaking the existing settings.  The default
logging configuration file looks like this:

```properties
log4j.rootLogger=WARN, A

log4j.appender.B=org.apache.log4j.ConsoleAppender
log4j.appender.B.layout=org.apache.log4j.PatternLayout
log4j.appender.B.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

log4j.appender.A=org.apache.log4j.RollingFileAppender
log4j.appender.A.File=/var/log/notificationagent/notificationagent.log
log4j.appender.A.layout=org.apache.log4j.PatternLayout
log4j.appender.A.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n
log4j.appender.A.MaxFileSize=10MB
log4j.appender.A.MaxBackupIndex=1
```

The easiest setting to change is the log level.  By default, the log level is
set to `WARN`, which is fairly quiet.  To obtain a little more information,
you can change the level to `INFO`.  If you need even more information then
you can set the level to either `DEBUG` or `TRACE`.

The notification agent also uses a rolling file appender, which prevents the
log files from growing too large.  By default, the maximum file size is
limited to ten megabytes and at most one backup log file will be retained.  To
change the maximum file size, you can change the `MaxFileSize` parameter.
Similarly, you can change the maximum number of backup log files by altering
the `MaxBackupIndex` parameter.

## Service Details

All service URLs are listed as relative URLs, and no parameters are included
in the URL.  Instead, every notification agent service except for the root
service (which merely returns a welcome message) accepts only POST requests
with a JSON request body.

### Verifying that the Notification Agent is Running

Endpoint: GET /

The root path in the notification agent can be used to verify that the
notification agent is actually running.  Sending a GET request to this service
will result in a welcome message being returned to the caller.  Here's an
example:

```
dennis$ curl http://by-tor:65533/
Welcome to the notification agent!
```

### Informing the Notification Agent of a Job Status Change

Endpoint: POST /job-status

This service intended to be used exclusively as an OSM callback for jobs.
Because of this, the request body that is sent to this service must be in the
format that is used to store the job state information in the OSM:

```json
{
    "analysis_name" : "Some analysis name",
    "completion_date" : "Tue Jan 31 2012 08:58:40 GMT-0700 (MST)",
    "analysis_description" : "Some analysis description",
    "status" : "status-name",
    "output_dir" : "/path/to/job/output/directory/",
    "uuid" : "job-identifier",
    "condor-log-dir" : "/path/to/directory/containing/condor/log/files/",
    "working_dir" : "/path/to/job/working/directory/",
    "email" : "someuser@somewhere.com",
    "jobs" : {
       "step-name" : {
          "stderr" : "/path/to/directory/containing/condor/log/files/step-name-stderr",
          "log-file" : "/path/to/directory/containing/condor/log/files/step-name-log",
          "status" : "status-name",
          "exit-code" : "0",
          "environment" : "environment-variable-settings",
          "exit-by-signal" : "true-if-process-was-killed",
          "executable" : "/path/to/executable",
          "arguments" : "command-line-arguments",
          "id" : "step-name",
          "stdout" : "/path/to/directory/containing/condor/log/files/step-name-stdout"
       },
       ...
    },
    "request_type" : "request-type-name",
    "execution_target" : "execution-target-name",
    "user" : "someuser",
    "dag_id" : "directed-acyclic-graph-identifier",
    "notify" : "true-if-user-wants-notification-emails",
    "submission_date" : "submission-date-as-milliseconds-since-the-epoch",
    "workspace_id" : "numeric-workspace-identifier",
    "name" : "job-name",
    "description" : "",
    "now_date" : "timestamp-of-most-recent-update",
    "output_manifest" : ["list-of-output-files"],
    "nfs_base" : "/path/to/nfs/mountpoint/",
    "irods_base" : "/path/to/base/irods/directory",
    "analysis_id" : "analysis-identifier"
}
```

If the service succeeds, a 200 status code is returned with no response body.
Otherwise, either a 400 or a 500 status code is returned and a brief
description of the problem is included in the response body.

### Getting Notifications from the Notification Agent

Endpoint: POST /get-messages
Endpoint: POST /get-unseen-messages

These two endpoints can be used to retrieve notifications from the
notification agent.  The former can be used to get notifications that have
been seen, notifications that haven't been seen yet, or both.  The latter can
only be used to retrieve notifications that haven't been seen yet.  Both
services accept a JSON request body in this format:

```json
{
    "user": "username",
    "seen": "seen-flag",
    "limit": "limit"
}
```

The `seen` flag is optional for both endpoints and is ignored by the
`/get-unseen-messages` endpoint.  The `limit` flag is optional for both
endpoints.  Here's an example:

```
dennis$ curl -sd '{
    "user": "ipctest",
    "seen": true,
    "limit": 1
}' http://by-tor:65533/get-messages | python -mjson.tool
{
    "messages": [
        {
            "deleted": false, 
            "message": {
                "id": "EE22CFF3-FA9B-4677-8642-4E542834536F", 
                "text": "job s2b_01310856 completed", 
                "timestamp": 1328025520000
            }, 
            "outputDir": "/path/to/analyses/directory/s2b_01310856-2012-01-31-08-56-18.059/", 
            "outputManifest": [], 
            "payload": {
                "action": "job_status_change", 
                "analysis_id": "a7909c999d97347dca20d6aba44fe5294", 
                "analysis_name": "Create BAM from SAM file", 
                "description": "", 
                "enddate": 1328025520000, 
                "id": "je1d7d621-4feb-4d78-84b2-8c1ec45d6d6d", 
                "name": "s2b_01310856", 
                "resultfolderid": "/path/to/analyses/directory/s2b_01310856-2012-01-31-08-56-18.059/", 
                "startdate": "1328025378059", 
                "status": "Completed", 
                "user": "somebody"
            }, 
            "seen": true, 
            "type": "analysis", 
            "user": "somebody", 
            "workspaceId": "2"
        }
    ]
}
```

### Deleting Notifications

Endpoint: POST /delete

"Deleting" a notification entails marking the notification as deleted in the
OSM so that it won't be returned by either the `/get-messages` service or the
`/get-unseen-messages` service.  This service accepts a request body in the
following format:

```json
{
    "uuids": [
        "some-uuid",
        "some-other-uuid",
        ...
    ]
}
```

If this service succeeds it returns a 200 status code with no response body.
Otherwise, it returns either a 400 status code or a 500 status code with a
brief description of the error.  An attempt to delete a message that has
already been marked as deleted does not result in an error.  Instead, the
service just treats the request as a no-op.  Similarly, an attempt to delete a
non-existent message is not treated as an error.  The service also treats this
condition as a no-op, but it does log a warning message indicating that
someone tried to delete a message that doesn't exist.  Here's an example:

```
dennis$ curl -sd '{
    "uuids": [
        "128C3FBD-B946-4292-8046-954B51CF7204",
        "EE22CFF3-FA9B-4677-8642-4E542834536F"
    ]
}' http://by-tor:65533/delete
```

### Unrecognized Service Path

If the notification agent doesn't recognize a service path then it will
respond with a 400 status code along with a message indicating that the
service path is not recognized.  Here's an example:

```
dennis$ curl -s http://by-tor:65533/foo
Unrecognized service path.
```
