package com.example.anki;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CardDao {

    // 追加
    @Insert
    void insert(Card card);

    // 保存されているデータ取得
    @Query("SELECT * FROM cards")
    List<Card> getAllCards();

    // カードのde-takousin
    @Update
    void update(Card card);

    // 削除
    @Delete
    void delete(Card card);
}