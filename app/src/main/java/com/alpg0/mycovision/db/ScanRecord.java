package com.alpg0.mycovision.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room entity — MUST have a no-arg constructor for Room to instantiate rows.
 */
@Entity(tableName = "scan_history")
public class ScanRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "image_uri")
    public String imageUri;

    @ColumnInfo(name = "label")
    public String label;

    @ColumnInfo(name = "confidence")
    public float confidence;

    /** Required no-arg constructor for Room */
    public ScanRecord() {}

    /** Convenience constructor — @Ignore so Room does NOT try to use it */
    @Ignore
    public ScanRecord(long timestamp, String imageUri, String label, float confidence) {
        this.timestamp = timestamp;
        this.imageUri  = imageUri;
        this.label     = label;
        this.confidence = confidence;
    }
}
