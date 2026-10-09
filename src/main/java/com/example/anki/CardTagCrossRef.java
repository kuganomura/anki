package com.example.anki;

import androidx.room.ColumnInfo;
import androidx.room.Entity;

//主キー
@Entity(tableName = "card_tag_cross_ref", primaryKeys = {"cardId", "tagId"})
public class CardTagCrossRef {

    @ColumnInfo(name = "cardId")
    public int cardId;

    @ColumnInfo(name = "tagId")
    public int tagId;
}