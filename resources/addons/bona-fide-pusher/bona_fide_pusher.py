#!/usr/bin/env python3

# A simple HTTP server listening REMS 'application.event/approved' event notifications.
# On notification it tries to push user id to Elixir for bona fide status.

# A configuration file 'config.ini' must be supplied.

# Usage: ./bona_fide_pusher.py
#        Stop with Ctrl-C

import configparser
import http.server
import json
import logging
import requests


parser = configparser.ConfigParser()
try:
    parser.read('config.ini')
    config = parser['default']
    url = config.get('url')
    port = config.getint('port')
    elixir_url = config.get('elixir_url')
    elixir_userid = config.get('elixir_userid')
    elixir_password = config.get('elixir_password')
except KeyError as e:
    log.error('Configuration error: missing key {0}'.format(e))
    exit(1)

logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=logging.DEBUG)
log = logging.getLogger(__name__)


class REMSBonaFideListener(http.server.BaseHTTPRequestHandler):
    def push_to_elixir(self, userid, eventid):
        elixir_auth = (elixir_userid, elixir_password)
        response = requests.post(elixir_url, data={'elixirid': userid}, auth=elixir_auth)
        log.info(eventid + 'Response: {0} {1}'.format(response.status_code, response.reason))
        return response

    def do_PUT(self):
        log.debug('Received PUT request, headers: {0}'.format(self.headers))
        length = int(self.headers['content-length'])
        payload = self.rfile.read(length)
        data = json.loads(payload)
        event_id = 'event/id:{0} '.format(data['event/id'])
        try:
            if data['event/type'] == 'application.event/approved':
                log.info(event_id + 'Received event notification: ' + data['event/type'])
                usrid = data['event/application']['application/applicant']['userid']
                log.info(event_id + 'Pushing bona fide status for user id: ' + usrid)
                r = self.push_to_elixir(usrid, event_id)
                if r.ok:
                    log.info(event_id + 'Pushed bona fide status successfully')
                    self.send_response(200, message='OK')
                else:
                    log.info(event_id + 'Failure at pushing bona fide status')
                    self.send_response(r.status_code, message=r.reason)
            else:
                log.info(event_id + 'Received illegal event type: ' + data['event/type'])
                self.send_response(400)
        except KeyError:
            msg = event_id + 'KeyError: Missing or invalid data!'
            log.debug(msg)
            log.debug(event_id + 'Data: {0}'.format(payload))
            self.send_response(400, message=msg)
            raise
        self.end_headers()
        return


if __name__ == "__main__":
    handler_class = REMSBonaFideListener
    http_server = http.server.HTTPServer((url, port), handler_class)
    with http_server:
        try:
            log.info('Event listener at \'{url}:{prt}\'. Stop with [Ctrl-C].'.format(url=url, prt=port))
            http_server.serve_forever()
        except KeyboardInterrupt:
            log.info('Event listener stopped')
