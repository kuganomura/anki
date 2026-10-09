package com.example.anki;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tags")
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // タグ名
    @ColumnInfo(name = "tag_name")
    public String tagName;
}