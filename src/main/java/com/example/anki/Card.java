package com.example.anki;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cards")
public class Card {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "question")
    public String question;

    @ColumnInfo(name = "answer")
    public String answer;

    @ColumnInfo(name = "last_result")
    public int lastResult = 0;

    //タグ（カテゴリ）を保存する項目
    @ColumnInfo(name = "category")
    public String category;

    // 正解数を保存する項目
    @ColumnInfo(name = "correct_count")
    public int correctCount = 0;

    // 不正解数を保存する項目
    @ColumnInfo(name = "incorrect_count")
    public int incorrectCount = 0;

    // 直近の履歴を保存する項目
    @ColumnInfo(name = "recent_results")
    public String recentResults = "";

}