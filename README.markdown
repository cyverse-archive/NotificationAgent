# Notification Agent
 
This service accepts either arbitrary notification requests or callback
notifications from the OSM about job status changes and provides endpoints for
the Discovery Environment that are used to look up the notifications for the
current user.  It also triggers the emails that are sent when e-mail
notifications are requested.

## Overview

The notification agent provides one endpoint that is designed to accept
arbitrary notification requests from arbitrary sources, one endpoint that is
designed to accept incoming job status updates from the OSM and three
endpoints that are designed to accept requests from the Discovery Environment.
The endpoint that accepts arbitrary notification requests is `/notification`,
which accepts JSON request bodies in the following format:

```json
{
    "type": "some_notification_type",
    "user": "some_user_name",
    "subject": "some notification subject",
    "message": "some message text",
    "email": true,
    "email_template": "some_email_template_name",
    "payload": {
        "email_address": "some@email.address"
    }
}
```

The `type`, `user` and `subject` fields are all required.  Failure to include
any of these fields in the request will cause the service to return an error
response.  The `message` field is optional and will default to the value of
the `subject` field if it's not provided.  The `email` field contains a flag
indicating whether or not an e-mail message should be sent.  This field is
optional and defaults to `false` if it's not provided.  The `email_template`
field is required if an e-mail is requested and must contain the name of an
e-mail template that is known to the iPlant e-mail service.  Failure to
include the name of a valid e-mail template in this field will result in the
e-mail message not being sent.  The `payload` field is required if an e-mail
message is requested, and should contain the user's e-mail address along with
any parameters that are required by the e-mail template.  All arbitrary
notifications are stored in the `notifications` bucket in the OSM.

The endpoint that accepts updates from the OSM is `/job-status`, which accepts
updates in the same format that is used by the JEX and Panopticon to store job
status information in the OSM:

```json
{
    "type": "some_notification_type",
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

Once a notification is stored in the `notifications` bucket of the OSM, it can
be retrieved from the notification agent using the `/messages` endpoint or the
`/unseen-messages` endpoint.  These endpoints are roughly equivalent except
that the latter can only be used to list messages that haven't been seen by
the user yet.  The `/messages` endpoint takes six query-string parameters:

<table>
    <thead>
        <tr><th>Name</th><th>Description</th><th>Required/Optional</th></tr>
    </thead>
    <tbody>
        <tr>
            <td>user</td>
            <td>The name of the user to retrieve notifications for.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>limit</td>
            <td>The maximum number of notifications to return at a time.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>offset</td>
            <td>The index of the starting message.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>sortField</td>
            <td>
                The field to use when sorting messages.  Currently, the only
                supported value for this field is `timestamp`.
            </td>
            <td>Optional (defaults to `timestamp`)</td>
        </tr>
        <tr>
            <td>sortDir</td>
            <td>
                The sorting direction, which can be `asc` (ascending) or `desc`
                (descending).
            </td>
            <td>Optional (defaults to `des`)</td>
        </tr>
        <tr>
            <td>filter</td>
            <td>
                Specifies the type of notification messages to return, which
                can be `data`, `analysis` or `tool`.  Other types of
                notifications may be added in the future.  If this parameter
                it not specified then all types of notifications will be
                returned.
            </td>
            <td>Optional</td>
        </tr>
    </tbody>
</table>

The `/unseen-messages` endpoint takes only the `user` parameter and returns all
of the notifications that haven't been marked as seen for that user.

Notifications may be marked as seen using the `/seen` endpoint.  This endpoint
accepts a JSON request body in the following format:

```json
{
    "uuids": [
        "some-uuid",
        "some-other-uuid",
        ...
    ]
}
```

This endpoint always succeeds as long as no unexpected errors occur.  If a
UUID that doesn't correspond to an actual notification is sent to this
endpoint then a warning message is written to the notification agent's log
file, but the problem is otherwise ignored.  If a notification that has
already been marked as seen is specified then the notification will not be
changed.

Messages that have been marked as seen will no longer be returned by the
`/unseen-messages` endpoint, but will continue to be returned by the
`/messages` endpoint as long as they are not marked as deleted.

Notifications that the user no longer wishes to see may be marked as deleted
so no message query will ever return the message again.  Messages are deleted
using the `/delete` endpoint.  The `/delete` endpoint takes a JSON request
body in the same format as the `/seen` endpoint.

This service will delete the messages corresponding to all of the identifiers
that are included in the request body.  An attempt to delete a message that
has already been marked as deleted is essentially a no-op, and will not cause
an error or warning message.  An attempt to delete a message that doesn't
exist will not cause an error, but it will cause a warning message to be
logged in the notification agent's log file.

## Notification Job Status Tracking

The notification agent keeps track of the status of each job that it sees,
which allows it to detect when the overall status of each job changes.  This
tracking is managed by storing records containing only the job identifier and
the last status of the job that was seen by the notification agent in the
`notificationagent_job_status` bucket of the OSM.

## Startup Tasks

If there is a configuration problem or the notification agent is down for an
extended period of time then it's possible for the status of a job to be
updated without the notification agent being aware of the change.  For this
reason, the notification agent scans the entire OSM upon startup, searching
for jobs for which the current job status doesn't match the status most
recently seen by the notification agent for that job.  One notification will
be generated for every job for which an inconsistent state is detected.

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
notificationagent.from-address=de@iplantcollaborative.org
notificationagent.from-name=iPlant DE Status Alert

# Notification recipients.
notificationagent.recipients=

# Listen port.
notificationagent.listen-port=65533
```

The OSM configuration settings tell the notification agent how to connect to
the OSM and which buckets to use.  The base URL is fairly self-explanatory.
The jobs bucket is the OSM bucket where the job status information is stored.
Similarly, the notifications bucket is the OSM bucket where the notifications
are stored.  The job status bucket is where the notification agent stores the
most recent status it has seen for each job.

The e-mail configuration settings are all fairly self-explanatory except for
the template, which is the name of the template that the e-mail service uses
when generating the message text for job status updates, and the from-address 
and from-name fields, which set the from email address and the name associated 
with it in the notification email.  In general, the template, from-address, 
and from-name settings will not change, but they have been made configurable 
in case we need to support different templates for different deployments. 

The `notificationagent.recipients` setting is a list of URLs to send
notifications to when a job status update is processed.  This feature is
intended to be used for server push, so this setting will probably be left
blank until server push is implemented or we find another use for this
feature.

The `notificationagent.listen-port` setting contains the port number that the
notification agent should listen to for incoming requests.

### Zookeeper Connection Information

One piece of information that can't be stored in Zookeeper is the information
required to connect to Zookeeper.  For the notification agent, this is stored
in a single file: `/etc/iplant-services/zkhosts.properties`.  Here's an
example:

```properties
zookeeper=by-tor:1234,snow-dog:4321
```

After installing the notification agent, it may be necessary to modify this
file so that it points to the correct host and port.

### Logging Settings

Since logging settings have to be changed fairly frequently for
troubleshooting, logging settings are not stored in Zookeeper.  Instead,
they're stored in a file on the local file system in
`/etc/notificationagent/log4j.properties'.  Since the notification agent uses
log4j, a lot of configuration settings are available.  See the [log4j
documentation](http://logging.apache.org/log4j/1.2/manual.html). for detailed
information about how to configure logging.

Even though complex logging configuration options are available, they're
normally not necessary because it's possible to exert a lot of control over
the logging behavior by simply tweaking the existing settings.  The default
logging configuration file looks like this:

```properties
log4j.rootLogger=WARN, A

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

All service URLs are listed as relative URLs.  The welcome endpoint, which can
be used to verify that the notification agent is running and responsive,
accepts GET requests with no query-string parameters.  Both message query
endpoints accept GET requests with parameters passed to the service in the
query string.  The rest of the services all accept POST requests with a JSON
request body.

### Verifying that the Notification Agent is Running

* Endpoint: GET /

The root path in the notification agent can be used to verify that the
notification agent is actually running.  Sending a GET request to this service
will result in a welcome message being returned to the caller.  Here's an
example:

```
dennis$ curl http://by-tor:65533/
Welcome to the notification agent!
```

### Requesting an Arbitrary Notification

* Endpoint: POST /notification

The purpose of this endpoint is to allow other iPlant services to request
notifications to be sent to the user.  The request body for this endpoint is
an abbreviated form of the notification format stored in the OSM.

```json
{
    "type": "some_notification_type",
    "user": "some_user_name",
    "subject": "some subject description",
    "message": "some message text",
    "email": true,
    "email_template": "some_template_name",
    "payload": {
        "email_address": "some@email.address"
    }
}
```

Only the `type`, `user` and `subject` fields are required.  The `type` field
contains the notification type, which currently must be known to the UI.  The
UI currently knows of three notification types `data`, `analysis` and `tool`.
The `user` field contains the user's unqualified username.  (For example, if
the full username is `nobody@iplantcollaborative.org` then the short username
is `nobody`.)  The `subject` field contains a brief description of the event
that prompted the notification.  The `message` field contains an optional
description of the event that prompted the notification.  If this field is not
provided then its value will default to that of the `subject` field.  The
`email` field contains a Boolean flag indicating whether or not an e-mail
message should be sent.  The value of this field defaults to `false` if not
provided.  The `email_template` field is required if an e-mail is requested,
and it must contain the name of an e-mail template that is known to the
iplant-email service.  The payload is optional and may contain arbitrary
information that may be of use to any recipient of the notification.  If an
e-mail is requested then this field must contain the user's e-mail address
along with any information required by the selected e-mail template.  Here's
an example:

```
curl -sd '
{
    "type": "nada",
    "user": "nobody",
    "subject": "nothing happened",
    "message": "nada y pues nada y pues nada",
    "email": true,
    "email_template": "nothing_happened",
    "payload": {
        "email_address": "nobody@iplantcollaborative.org"
    }
}
' http://services-2:31320/notification | python -mjson.tool
{
    "success": true
}
```

Note that this example is fictional and will not actually send an e-mail
message because the requested e-mail template doesn't exist.  The notification
type is also not known to the UI, which will cause errors in the UI.

If the service succeeds, a 200 status code is returned with a simple JSON
response body indicating that the service call succeeded.  Otherwise, either a
400 or a 500 status code is returned and a brief description of the problem is
included in the response body.

### Informing the Notification Agent of a Job Status Change

* Endpoint: POST /job-status

This service intended to be used exclusively as an OSM callback for jobs.
Because of this, the request body that is sent to this service must be in the
format that is used to store the job state information in the OSM:

```json
{
    "type": "some_notification_type",
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

If the service succeeds, a 200 status code is returned with a simple JSON
response body indicating that the service call succeeded.  Otherwise, either a
400 or a 500 status code is returned and a brief description of the problem is
included in the response body.

### Getting Notifications from the Notification Agent

* Endpoint: GET /messages
* Endpoint: GET /unseen-messages

These two endpoints can be used to retrieve notifications from the
notification agent.  The former can be used to get both notifications that
have already been seen and notifications that haven't been seen yet.  It is
very likely that each user will have a lot of notifications that have already
been seen, so the `/messages` endpoint provides a paginated view, with both
the number of messages and the index of the starting message specified in the
query string.  The full list of query string parameters for this endpoint is:

<table>
    <thead>
        <tr><th>Name</th><th>Description</th><th>Required/Optional</th></tr>
    </thead>
    <tbody>
        <tr>
            <td>user</td>
            <td>The name of the user to retrieve notifications for.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>limit</td>
            <td>
                The maximum number of notifications to return at a time or `0`
                if there is no limit.
            </td>
            <td>Optional (defaults to `0`)</td>
        </tr>
        <tr>
            <td>offset</td>
            <td>The index of the starting message.</td>
            <td>Optional (defaults to `0`)</td>
        </tr>
        <tr>
            <td>seen</td>
            <td>
                Indicates whether messages that the user has seen, messages that
                the user has not seen, or both should be returned.  If the value
                of this parameter is set to `true` then only messages that the
                user has seen will be returned.  If the value of this parameter
                is `false` then only messages that the user has not seen will be
                returned.  If the this parameter is not specified at all then
                both messages that the user has seen and messages that the user
                has not seen will be returned.
            </td>
            <td>Optional</td>
        </tr>
        <tr>
            <td>sortField</td>
            <td>
                The field to use when sorting messages.  Currently, the only
                supported value for this field is `timestamp`.
            </td>
            <td>Optional (defaults to `timestamp`)</td>
        </tr>
        <tr>
            <td>sortDir</td>
            <td>
                The sorting direction, which can be `asc` (ascending) or `desc`
                (descending).
            </td>
            <td>Optional (defaults to `desc`)</td>
        </tr>
        <tr>
            <td>filter</td>
            <td>
                Specifies the type of notification messages to return, which
                can be `data`, `analysis` or `tool`.  Other types of
                notifications may be added in the future.  If this parameter
                it not specified then all types of notifications will be
                returned.
            </td>
            <td>Optional</td>
        </tr>
    </tbody>
</table>

The `/unseen-messages` endpoint can only be used to obtain messages that
haven't been marked as seen yet.  This service does not provide a paginated
view because it is likely that users will want to receive any notifications
that they haven't seen already immediately.  This endpoint accepts only the
`user` query-string parameter.

Here are some examples:

```
$ curl -s 'http://by-tor:65533/messages?user=ipctest&limit=1&offset=0' | python -mjson.tool
{
    "messages": [
        {
            "deleted": false, 
            "message": {
                "id": "6DF4475F-EE81-4063-B457-6EDFA4ED9C5F", 
                "text": "cat_06221137 completed", 
                "timestamp": 1340390304000
            }, 
            "outputDir": "/iplant/home/ipctest/analyses/cat_06221137-2012-06-22-11-37-58.636", 
            "outputManifest": [], 
            "payload": {
                "action": "job_status_change", 
                "analysis_id": "a508674c3c9464ccbbbcf1600650db446", 
                "analysis_name": "Concatenate Multiple Files", 
                "description": "", 
                "enddate": 1340390297000, 
                "id": "jf408fc4d-628c-41bb-819c-4b5de9cb24e0", 
                "name": "cat_06221137", 
                "resultfolderid": "/iplant/home/ipctest/analyses/cat_06221137-2012-06-22-11-37-58.636", 
                "startdate": "1340390278636", 
                "status": "Completed", 
                "user": "ipctest"
            }, 
            "seen": true, 
            "type": "analysis", 
            "user": "ipctest", 
            "workspaceId": "39"
        }
    ], 
    "total": "256"
}
```

```
$ curl -s 'http://by-tor:65533/unseen-messages?user=ipctest' | python -mjson.tool
{
    "messages": [], 
    "total": "0"
}
```

### Counting Notifications

* Endpoint: GET /count-messages

In some cases, it's useful to be able to obtain the number of messages that
match a set of criteria without retrieving the messages themselves.  This
endpoint is provided for this purpose.

This endpoint takes three query-string parameters:

<table>
    <thead>
        <tr><th>Name</th><th>Description</th><th>Required/Optional</th></tr>
    </thead>
    <tbody>
        <tr>
            <td>user</td>
            <td>The name of the user to retrieve notifications for.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>seen</td>
            <td>
                Indicates whether messages that the user has seen, messages that
                the user has not seen, or both should be returned.  If the value
                of this parameter is set to `true` then only messages that the
                user has seen will be returned.  If the value of this parameter
                is `false` then only messages that the user has not seen will be
                returned.  If the this parameter is not specified at all then
                both messages that the user has seen and messages that the user
                has not seen will be returned.
            </td>
            <td>Optional</td>
        </tr>
        <tr>
            <td>filter</td>
            <td>
                Specifies the type of notification messages to return, which
                can be `data`, `analysis` or `tool`.  Other types of
                notifications may be added in the future.  If this parameter
                it not specified then all types of notifications will be
                returned.
            </td>
            <td>Optional</td>
        </tr>
    </tbody>
</table>

The response body consists of a JSON object with a single field, `total`, that
contains the number of messages that match the criteria specified in the query
string:

```json
{
    "total": message-count
}
```

Here are some examples:

```
$ curl -s "http://by-tor:65533/count-messages?user=ipctest" | python -mjson.tool
{
    "total": "409"
}
```

In this example, all messages for the user, `ipctest`, are counted.

```
$ curl -s "http://by-tor:65533/count-messages?user=ipctest&filter=data" | python -mjson.tool
{
    "total": "91"
}
```

In this example, all data messages for the user, `ipctest`, are counted.

```
$ curl -s "http://services-2:31320/count-messages?user=ipctest&filter=data&seen=false" | python -mjson.tool
{
    "total": "0"
}
```

In this example, only unseen data messages for the uer, `ipctest`, are counted.

### Marking Notifications as Seen

* Endpoint: POST /seen

Marking a notification as seen prevents it from being returned by the
`/unseen-messages` endpoint.  The intent is for this endpoint to be called
when the user has seen a notification for the first time.  This service
accepts a request body in the following format:

```json
{
    "uuids": [
        "some-uuid",
        "some-other-uuid",
        ...
    ]
}
```

If this service succeeds, it returns a 200 status code with a simple JSON
response body indicating that the service call suceeded.  Otherwise, it
returns either a 400 or a 500 status code with a brief description of the
error.  An attempt to mark a non-existent message as seen does not cause the
service call to fail, but a warning message will be logged in the notification
agent's log file.  An attempt to mark a message that has already been marked
as seen is silently ignored.  Here's an example:

```
$ curl -sd '
{
    "uuids": [
        "6DF4475F-EE81-4063-B457-6EDFA4ED9C5F"
    ]
}
' http://services-2:31320/seen | python -mjson.tool
{
    "success": true
}
```

### Deleting Notifications

* Endpoint: POST /delete

"Deleting" a notification entails marking the notification as deleted in the
OSM so that it won't be returned by either the `/messages` service or the
`/unseen-messages` service.  This service accepts a request body in the
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

If this service succeeds it returns a 200 status code with a simple JSON
response body indicating that the service call succeeded.  Otherwise, it
returns either a 400 status code or a 500 status code with a brief description
of the error.  An attempt to delete a message that has already been marked as
deleted does not result in an error.  Instead, the service just treats the
request as a no-op.  Similarly, an attempt to delete a non-existent message is
not treated as an error.  The service also treats this condition as a no-op,
but it does log a warning message indicating that someone tried to delete a
message that doesn't exist.  Here's an example:

```
$ curl -sd '
{
    "uuids": [
        "361C2A67-3942-4F7A-9734-E5B8B28FDC12"
    ]
}
' http://by-tor:65533/delete | python -mjson.tool
{
    "success": true
}
```

### Unrecognized Service Path

If the notification agent doesn't recognize a service path then it will
respond with a 400 status code along with a message indicating that the
service path is not recognized.  Here's an example:

```
dennis$ curl -s http://by-tor:65533/foo
Unrecognized service path.
```
