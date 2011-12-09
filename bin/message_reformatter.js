/*
 * The message format expected by the DE isn't exactly the same as the format
 * that is produced by the notification agent.  Timestamps are stored in the
 * OSM in the same format produced by Date.toString(), but the DE requires
 * them to be represented by the number of milliseconds since 1/1/1970.  The
 * timestamp format produced in the OSM is used in several places, and is in
 * a human readable format, so the timestamps will be modified when the
 * messages are sent.
 *
 * returns:
 *   the message reformatter.
 */
exports.create = function() {

    /*
     * Updates a single timestamp.  If the timestamp is undefined or empty
     * then the empty string will be returned.  Otherwise, the timestamp
     * will be parsed and reformatted and returned.
     *
     * params:
     *   timestamp - the original timestamp.
     * returns:
     *   the updated timestamp or the empty string.
     */
    var update_timestamp = function(timestamp) {
        var result = '';
        if (typeof timestamp !== 'undefined' && timestamp !== '') {
            result = new Date(timestamp).getTime();
        }
        return result;
    }

    /*
     * Updates all of the timestamps in the given message.
     *
     * params:
     *   message - the message to update.
     */
    var update_timestamps = function(message) {
        message.message.timestamp = update_timestamp(message.message.timestamp);
        message.payload.startdate = update_timestamp(message.payload.startdate);
        message.payload.enddate = update_timestamp(message.payload.enddate);
    }

    return {

        /*
         * Reformats a single message.
         *
         * params:
         *   message - the message to reformat.
         * returns:
         *   the reformatted message.
         */
        reformat: function(message) {
            update_timestamps(message);
            return message;
        }
    }
}
