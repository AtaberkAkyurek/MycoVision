package com.alpg0.mycovision.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScanDao {

    @Insert
    long insertScan(ScanRecord record);

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    List<ScanRecord> getAllScans();

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    ScanRecord getScanById(long id);

    @Query("DELETE FROM scan_history WHERE id = :id")
    void deleteScanById(long id);

    @Query("DELETE FROM scan_history")
    void deleteAll();
}
