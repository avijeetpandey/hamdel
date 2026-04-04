import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const errorRate    = new Rate('errors');
const ingestTrend  = new Trend('ingest_latency', true);

export const options = {
  stages: [
    { duration: '30s', target: 100  },   // ramp up
    { duration: '2m',  target: 1000 },   // steady high load
    { duration: '30s', target: 2000 },   // spike
    { duration: '30s', target: 0    },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<200'],     // local-dev SLA: P99 < 200ms (use p(99)<5 for prod cluster)
    errors:            ['rate<0.01'],     // error rate < 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    eventId:           uuidv4(),
    sessionId:         `session-${Math.floor(Math.random() * 10000)}`,
    clientId:          `client-${__VU}`,
    contentId:         'movie-inception-4k',
    timestampMs:       Date.now(),
    videoStartTimeMs:  Math.random() * 500,
    playbackFailed:    Math.random() < 0.02,
    rebufferDurationMs: Math.random() * 3000,
    playbackDurationMs: 60000 + Math.random() * 3600000,
    playerVersion:     '5.2.1',
    os:                'iOS',
    cdn:               'cloudfront',
  });

  const res = http.post(`${BASE_URL}/api/v1/heartbeat`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const ok = check(res, { 'status 202': r => r.status === 202 });
  errorRate.add(!ok);
  ingestTrend.add(res.timings.duration);

  sleep(0.001);
}
