# deploy/k8s — kustomize manifests

One image serves every role; a route is just env (`SPRING_PROFILES_ACTIVE`,
`APP_SOURCES_ENABLED`, `APP_FTP_ENABLED`). Point each ArgoCD Application at one
overlay directory.

```
base/worker/            Deployment skeleton (profile worker)
base/gateway/           Deployment + Service (profile gateway)
overlays/worker-route-a    consumes source-a only + KEDA ScaledObject
overlays/worker-route-ab   consumes source-a,source-b, two KEDA triggers
overlays/gateway-http      HTTP-only gateway (FTP poller off)
overlays/gateway-ftp       FTP-polling gateway
monitoring/             prometheus-nats-exporter + PrometheusRule alert set
```

Render locally: `kubectl kustomize deploy/k8s/overlays/worker-route-a`

## Hard rule: one (stream, durable) pair per Deployment

A durable's pending count is the KEDA scaling signal. If two Deployments
consume the same (stream, durable), their two ScaledObjects fight over the
same lag number and scale against each other. If two sites must both consume
source-a, give each its own durable (independent consumption progress) — or
they are different NATS clusters anyway. Never list the same source in two
overlays that target the same NATS cluster and durable.

Expected secrets (create per cluster, out of band): `ingest-db`
(username/password), `ingest-gateway` (token), `ingest-ftp` (username/password
— only for gateway-ftp).

KEDA's `nats-jetstream` trigger and the `PrometheusRule` CRD require KEDA and
prometheus-operator installed in the cluster.

## Recipe: add a worker route (e.g. route-c consuming source-c)

Copy `overlays/worker-route-a/` → `overlays/worker-route-c/`, then change
exactly these values — nothing else:

1. `kustomization.yaml`: `nameSuffix: -route-c`
2. `sources-patch.yaml`: `value: source-c`
3. `scaledobject.yaml`: `scaleTargetRef.name: ingest-worker-route-c`,
   `stream: "SRC_C_EVENTS"`, `consumer: "ingest-worker-src-c"` — must match
   the source's block in `application-worker.yml` exactly.
4. Check the hard rule above: no other overlay on this cluster may already
   own that (stream, durable).

A route consuming several sources: copy `worker-route-ab/` instead and add one
trigger per durable.
