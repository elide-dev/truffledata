package dev.elide.truffledata;

/**
 * Validated immutable storage whose payload can be traversed without guest library dispatch.
 * Adapters may expose this storage through {@link AbstractRow#payloadStorage()} only when it has
 * exactly the same schema and field values as their facade.
 */
public sealed interface SnapshotStorage permits StaticLayout.Record, Batch.Row {
  Schema schema();
}
