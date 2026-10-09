package com.example.anki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.anki.databinding.FragmentFirstBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: CardAdapter

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var currentCards: List<Card> = emptyList()

    private val standardTags = listOf("タグなし", "国語", "数学", "理科", "社会", "英語")
    private val ADD_NEW_TAG_OPTION = "＋ 新しいタグを追加..."

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        adapter = CardAdapter()

        binding.recyclerViewCards.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCards.adapter = adapter

        loadCards()

        val rootLayout = binding.root as? LinearLayout

        // カレンダーを開くボタン
        val calendarButton = Button(requireContext()).apply {
            text = "🗓️ 学習カレンダーを開く"
            setOnClickListener { showCalendarDialog() }
        }

        // デイリーテスト用ボタン
        val dailyButton = Button(requireContext()).apply {
            text = "📅 今日のデイリーテスト（3問）"
        }

        // デイリー状態のチェック
        val prefs = requireContext().getSharedPreferences("AnkiPrefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDailyDate = prefs.getString("DAILY_LAST_DATE", "")

        if (lastDailyDate == today) {
            dailyButton.visibility = View.GONE
        } else {
            dailyButton.visibility = View.VISIBLE
        }

        dailyButton.setOnClickListener {
            if (currentCards.size < 3) {
                Toast.makeText(requireContext(), "デイリーテストには問題が3問以上必要です", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bundle = Bundle().apply {
                putBoolean("IS_DAILY", true)
            }
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }

        val highlightButton = Button(requireContext()).apply {
            text = "📊 弱点・得意ハイライト"
            setOnClickListener { showHighlightDialog() }
        }

        // ★追加：ゴミ箱を開くボタン
        val trashButton = Button(requireContext()).apply {
            text = "♻️ ゴミ箱（一時保存）を見る"
            setOnClickListener { showTrashDialog() }
        }

        // まとめて削除モードUI
        val batchDeleteButton = Button(requireContext()).apply {
            text = "🗑️ まとめて削除モード"
        }

        val deleteControlsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }

        val confirmDeleteButton = Button(requireContext()).apply {
            text = "✔️ 選択した問題をゴミ箱へ"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cancelDeleteButton = Button(requireContext()).apply {
            text = "キャンセル"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        deleteControlsContainer.addView(confirmDeleteButton)
        deleteControlsContainer.addView(cancelDeleteButton)

        // ボタンを画面最上部に追加（上から順に並ぶように）
        rootLayout?.addView(deleteControlsContainer, 0)
        rootLayout?.addView(batchDeleteButton, 0)
        rootLayout?.addView(trashButton, 0) // ゴミ箱ボタンを追加
        rootLayout?.addView(highlightButton, 0)
        rootLayout?.addView(dailyButton, 0)
        rootLayout?.addView(calendarButton, 0)

        batchDeleteButton.setOnClickListener {
            adapter.toggleDeleteMode(true)
            batchDeleteButton.visibility = View.GONE
            deleteControlsContainer.visibility = View.VISIBLE
        }

        cancelDeleteButton.setOnClickListener {
            adapter.toggleDeleteMode(false)
            batchDeleteButton.visibility = View.VISIBLE
            deleteControlsContainer.visibility = View.GONE
        }

        confirmDeleteButton.setOnClickListener {
            val cardsToDelete = adapter.selectedCards

            if (cardsToDelete.isEmpty()) {
                Toast.makeText(requireContext(), "移動する問題が選択されていません", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("確認")
                .setMessage("${cardsToDelete.size}件の問題をゴミ箱に移動しますか？\n(後から復元できます)")
                .setPositiveButton("ゴミ箱へ") { _, _ ->
                    executor.execute {
                        cardsToDelete.forEach { card ->
                            // ★変更：完全に消さずにカテゴリをゴミ箱にする
                            card.category = "🗑️ゴミ箱"
                            db.cardDao().update(card)
                        }
                        handler.post {
                            Toast.makeText(requireContext(), "${cardsToDelete.size}件をゴミ箱に移動しました", Toast.LENGTH_SHORT).show()
                            adapter.toggleDeleteMode(false)
                            batchDeleteButton.visibility = View.VISIBLE
                            deleteControlsContainer.visibility = View.GONE
                            loadCards()
                        }
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        binding.buttonAddCard.setOnClickListener {
            showAddDialog()
        }

        binding.buttonStartMemorization.setOnClickListener {
            val selectedTag = binding.spinnerTagFilter.selectedItem?.toString() ?: "すべて"

            val targetCards = if (selectedTag == "すべて") {
                currentCards
            } else {
                currentCards.filter { it.category == selectedTag }
            }

            if (targetCards.isEmpty()) {
                Toast.makeText(requireContext(), "該当する問題がありません", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("SELECTED_TAG", selectedTag)
                putBoolean("IS_DAILY", false)
            }
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }

        adapter.setOnStarClickListener { card ->
            showEditOrDeleteDialog(card)
        }
    }

    // ★追加：ゴミ箱のリストを表示するダイアログ
    private fun showTrashDialog() {
        executor.execute {
            // データベースから「🗑️ゴミ箱」カテゴリのものだけを取得
            val trashCards = db.cardDao().allCards.filter { it.category == "🗑️ゴミ箱" }

            handler.post {
                if (trashCards.isEmpty()) {
                    Toast.makeText(requireContext(), "ゴミ箱は空です", Toast.LENGTH_SHORT).show()
                    return@post
                }

                val items = trashCards.map { "・${it.question}" }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("♻️ ゴミ箱 (タップして操作)")
                    .setItems(items) { _, which ->
                        val selectedCard = trashCards[which]
                        showTrashActionDialog(selectedCard)
                    }
                    .setPositiveButton("閉じる", null)
                    .show()
            }
        }
    }

    // ★追加：ゴミ箱の中のアイテムを復元するか完全に消すか選ぶダイアログ
    private fun showTrashActionDialog(card: Card) {
        AlertDialog.Builder(requireContext())
            .setTitle("カードの復元・削除")
            .setMessage("問題: ${card.question}\n\nこの問題をどうしますか？")
            .setPositiveButton("復元する") { _, _ ->
                card.category = "タグなし" // 復元時は一旦「タグなし」に戻す
                executor.execute {
                    db.cardDao().update(card)
                    loadCards()
                    handler.post { Toast.makeText(requireContext(), "復元しました", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("完全に削除") { _, _ ->
                // 完全に消す前の最終確認
                AlertDialog.Builder(requireContext())
                    .setTitle("最終確認")
                    .setMessage("本当に完全に削除しますか？\n(この操作は取り消せません)")
                    .setPositiveButton("完全削除") { _, _ ->
                        executor.execute {
                            db.cardDao().delete(card) // ここで本当にデータベースから消去
                            loadCards()
                            handler.post { Toast.makeText(requireContext(), "完全に削除しました", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            .setNeutralButton("キャンセル", null)
            .show()
    }

    private fun showCalendarDialog() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("AnkiPrefs", Context.MODE_PRIVATE)

        var displayCalendar = Calendar.getInstance()

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 30)
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val prevBtn = Button(context).apply { text = "◀" }
        val monthTitleText = TextView(context).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(30, 0, 30, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val nextBtn = Button(context).apply { text = "▶" }

        headerLayout.addView(prevBtn)
        headerLayout.addView(monthTitleText)
        headerLayout.addView(nextBtn)
        mainLayout.addView(headerLayout)

        val weekHeaderLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 10)
        }
        val weekDays = listOf("日", "月", "火", "水", "木", "金", "土")
        weekDays.forEachIndexed { index, day ->
            val tv = TextView(context).apply {
                text = day
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index == 0) setTextColor(Color.RED)
                if (index == 6) setTextColor(Color.BLUE)
            }
            weekHeaderLayout.addView(tv)
        }
        mainLayout.addView(weekHeaderLayout)

        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(gridContainer)

        val legendText = TextView(context).apply {
            text = "◎ : 全問正解  /  ☆ : 挑戦完了"
            textSize = 14f
            setPadding(0, 25, 0, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        mainLayout.addView(legendText)

        fun updateCalendarGrid() {
            gridContainer.removeAllViews()

            val yearMonthFormat = SimpleDateFormat("yyyy年 M月", Locale.getDefault())
            monthTitleText.text = yearMonthFormat.format(displayCalendar.time)

            val cal = displayCalendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            val totalCells = firstDayOfWeek + daysInMonth
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            var currentCell = 0
            while (currentCell < totalCells) {
                val weekRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                for (i in 0 until 7) {
                    val cellLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        setPadding(2, 6, 2, 6)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    if (currentCell >= firstDayOfWeek && (currentCell - firstDayOfWeek + 1) <= daysInMonth) {
                        val dayNum = currentCell - firstDayOfWeek + 1
                        cal.set(Calendar.DAY_OF_MONTH, dayNum)
                        val dateStr = dateFormat.format(cal.time)

                        val result = prefs.getString("DAILY_RESULT_$dateStr", null)
                        val mark = when (result) {
                            "STAR" -> "◎"
                            "CIRCLE" -> "☆"
                            else -> ""
                        }

                        val dayTv = TextView(context).apply {
                            text = dayNum.toString()
                            textSize = 13f
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                        }
                        val markTv = TextView(context).apply {
                            text = mark
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            if (mark == "◎") setTextColor(Color.RED)
                            else if (mark == "☆") setTextColor(Color.BLUE)
                        }

                        cellLayout.addView(dayTv)
                        cellLayout.addView(markTv)
                    }

                    weekRow.addView(cellLayout)
                    currentCell++
                }
                gridContainer.addView(weekRow)
            }
        }

        updateCalendarGrid()

        prevBtn.setOnClickListener {
            displayCalendar.add(Calendar.MONTH, -1)
            updateCalendarGrid()
        }

        nextBtn.setOnClickListener {
            displayCalendar.add(Calendar.MONTH, 1)
            updateCalendarGrid()
        }

        AlertDialog.Builder(context)
            .setTitle("🗓️ 学習カレンダー")
            .setView(mainLayout)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun loadCards() {
        executor.execute {
            val allCards = db.cardDao().allCards
            // ★変更：ゴミ箱に入っているカードを除外して表示する
            val activeCards = allCards.filter { it.category != "🗑️ゴミ箱" }

            handler.post {
                currentCards = activeCards
                adapter.setCards(activeCards)
                updateFilterSpinner()
            }
        }
    }

    private fun updateFilterSpinner() {
        val usedCustomTags = currentCards.mapNotNull { it.category }
            .distinct()
            .filter { it !in standardTags && it.isNotEmpty() && it != "🗑️ゴミ箱" } // ゴミ箱タグは念のため除外
            .sorted()

        val filterList = mutableListOf("すべて")
        filterList.addAll(standardTags)
        filterList.addAll(usedCustomTags)

        val currentSelection = binding.spinnerTagFilter.selectedItem?.toString() ?: "すべて"

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterList)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTagFilter.adapter = spinnerAdapter

        val position = filterList.indexOf(currentSelection)
        if (position >= 0) {
            binding.spinnerTagFilter.setSelection(position)
        }
    }

    private fun getDialogSpinnerItems(): MutableList<String> {
        val usedCustomTags = currentCards.mapNotNull { it.category }
            .distinct()
            .filter { it !in standardTags && it.isNotEmpty() && it != "🗑️ゴミ箱" }
            .sorted()

        val items = mutableListOf<String>()
        items.addAll(standardTags)
        items.addAll(usedCustomTags)
        items.add(ADD_NEW_TAG_OPTION)
        return items
    }

    private fun showAddDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val questionInput = EditText(context).apply { hint = "問題" }
        val answerInput = EditText(context).apply { hint = "答え" }

        val tagContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val spinnerItems = getDialogSpinnerItems()
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, spinnerItems).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val tagSpinner = Spinner(context).apply {
            adapter = spinnerAdapter
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteTagButton = Button(context).apply {
            text = "−"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }

        tagContainer.addView(tagSpinner)
        tagContainer.addView(deleteTagButton)

        val customTagInput = EditText(context).apply {
            hint = "新しいタグを入力"
            visibility = View.GONE
        }

        tagSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = spinnerItems[position]
                if (selected == ADD_NEW_TAG_OPTION) {
                    customTagInput.visibility = View.VISIBLE
                    deleteTagButton.visibility = View.GONE
                } else if (selected !in standardTags) {
                    customTagInput.visibility = View.GONE
                    deleteTagButton.visibility = View.VISIBLE
                } else {
                    customTagInput.visibility = View.GONE
                    deleteTagButton.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        deleteTagButton.setOnClickListener {
            val tagToDelete = tagSpinner.selectedItem.toString()
            executor.execute {
                val cardsWithTag = db.cardDao().allCards.filter { it.category == tagToDelete }
                for (c in cardsWithTag) {
                    c.category = "タグなし"
                    db.cardDao().update(c)
                }
                handler.post {
                    Toast.makeText(context, "タグ「$tagToDelete」を削除しました", Toast.LENGTH_SHORT).show()
                    spinnerItems.remove(tagToDelete)
                    spinnerAdapter.notifyDataSetChanged()
                    tagSpinner.setSelection(spinnerItems.indexOf("タグなし"))
                    loadCards()
                }
            }
        }

        layout.addView(questionInput)
        layout.addView(answerInput)
        layout.addView(tagContainer)
        layout.addView(customTagInput)

        AlertDialog.Builder(context)
            .setTitle("追加する問題")
            .setView(layout)
            .setPositiveButton("追加") { _, _ ->
                val q = questionInput.text.toString()
                val a = answerInput.text.toString()

                val selectedItem = tagSpinner.selectedItem.toString()
                val tag = if (selectedItem == ADD_NEW_TAG_OPTION) {
                    val input = customTagInput.text.toString().trim()
                    if (input.isEmpty()) "タグなし" else input
                } else {
                    selectedItem
                }

                if (q.isNotEmpty() && a.isNotEmpty()) {
                    val newCard = Card()
                    newCard.question = q
                    newCard.answer = a
                    newCard.category = tag

                    executor.execute {
                        db.cardDao().insert(newCard)
                        loadCards()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showEditOrDeleteDialog(card: Card) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val questionInput = EditText(context).apply {
            hint = "問題"
            setText(card.question)
        }

        val answerInput = EditText(context).apply {
            hint = "答え"
            setText(card.answer)
        }

        val tagContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val spinnerItems = getDialogSpinnerItems()
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, spinnerItems).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val tagSpinner = Spinner(context).apply {
            adapter = spinnerAdapter
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteTagButton = Button(context).apply {
            text = "−"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }

        tagContainer.addView(tagSpinner)
        tagContainer.addView(deleteTagButton)

        val customTagInput = EditText(context).apply {
            hint = "新しいタグを入力"
            visibility = View.GONE
        }

        tagSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = spinnerItems[position]
                if (selected == ADD_NEW_TAG_OPTION) {
                    customTagInput.visibility = View.VISIBLE
                    deleteTagButton.visibility = View.GONE
                } else if (selected !in standardTags) {
                    customTagInput.visibility = View.GONE
                    deleteTagButton.visibility = View.VISIBLE
                } else {
                    customTagInput.visibility = View.GONE
                    deleteTagButton.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        deleteTagButton.setOnClickListener {
            val tagToDelete = tagSpinner.selectedItem.toString()
            executor.execute {
                val cardsWithTag = db.cardDao().allCards.filter { it.category == tagToDelete }
                for (c in cardsWithTag) {
                    c.category = "タグなし"
                    db.cardDao().update(c)
                }
                handler.post {
                    Toast.makeText(context, "タグ「$tagToDelete」を削除しました", Toast.LENGTH_SHORT).show()
                    spinnerItems.remove(tagToDelete)
                    spinnerAdapter.notifyDataSetChanged()
                    tagSpinner.setSelection(spinnerItems.indexOf("タグなし"))
                    loadCards()
                }
            }
        }

        val currentCategoryIndex = spinnerItems.indexOf(card.category)
        if (currentCategoryIndex >= 0) {
            tagSpinner.setSelection(currentCategoryIndex)
        } else {
            tagSpinner.setSelection(spinnerItems.indexOf(ADD_NEW_TAG_OPTION))
            customTagInput.setText(card.category)
            customTagInput.visibility = View.VISIBLE
        }

        layout.addView(questionInput)
        layout.addView(answerInput)
        layout.addView(tagContainer)
        layout.addView(customTagInput)

        AlertDialog.Builder(context)
            .setTitle("カードの編集")
            .setMessage("内容を書き換えるか、削除を選んでください。")
            .setView(layout)
            .setPositiveButton("更新") { _, _ ->
                val newQ = questionInput.text.toString()
                val newA = answerInput.text.toString()

                val selectedItem = tagSpinner.selectedItem.toString()
                val newTag = if (selectedItem == ADD_NEW_TAG_OPTION) {
                    val input = customTagInput.text.toString().trim()
                    if (input.isEmpty()) "タグなし" else input
                } else {
                    selectedItem
                }

                if (newQ.isNotEmpty() && newA.isNotEmpty()) {
                    card.question = newQ
                    card.answer = newA
                    card.category = newTag

                    executor.execute {
                        db.cardDao().update(card)
                        loadCards()
                    }
                } else {
                    Toast.makeText(context, "問題と答えは空にできません", Toast.LENGTH_SHORT).show()
                }
            }
            // ★変更：完全に消すのではなくゴミ箱に移動する処理に変更
            .setNeutralButton("ゴミ箱へ移動") { _, _ ->
                executor.execute {
                    card.category = "🗑️ゴミ箱"
                    db.cardDao().update(card)
                    loadCards()
                    handler.post { Toast.makeText(context, "ゴミ箱に移動しました", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showHighlightDialog() {
        val context = requireContext()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val usedCustomTags = currentCards.mapNotNull { it.category }
            .distinct()
            .filter { it !in standardTags && it.isNotEmpty() && it != "🗑️ゴミ箱" }
            .sorted()
        val categoryList = mutableListOf("総合").apply {
            addAll(standardTags)
            addAll(usedCustomTags)
        }

        var currentCategoryIndex = 0

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        val prevButton = Button(context).apply { text = "◀" }
        val categoryText = TextView(context).apply {
            text = categoryList[currentCategoryIndex]
            textSize = 20f
            setPadding(30, 0, 30, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val nextButton = Button(context).apply { text = "▶" }

        headerLayout.addView(prevButton)
        headerLayout.addView(categoryText)
        headerLayout.addView(nextButton)

        val rankingText = TextView(context).apply {
            textSize = 16f
            setPadding(10, 40, 10, 10)
        }

        layout.addView(headerLayout)
        layout.addView(rankingText)

        fun updateRankingText() {
            val targetCategory = categoryList[currentCategoryIndex]
            val targetCards = if (targetCategory == "総合") {
                currentCards
            } else {
                currentCards.filter { it.category == targetCategory }
            }

            val cardStats = targetCards.map { card ->
                val results = if (card.recentResults.isNullOrEmpty()) emptyList<String>() else card.recentResults.split(",")
                val recentCorrect = results.count { it == "1" }
                val recentIncorrect = results.count { it == "0" }
                Triple(card, recentCorrect, recentIncorrect)
            }

            val worstCards = cardStats.filter { it.third > 0 }
                .sortedByDescending { it.third }
                .take(3)

            val bestCards = cardStats.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)

            val sb = StringBuilder()
            sb.append("【💔直近でよく間違える問題】\n")
            if (worstCards.isEmpty()) sb.append("データなし\n")
            worstCards.forEach { sb.append("・${it.first.question}（直近ミス ${it.third}回）\n") }

            sb.append("\n【✨直近でよく正解する問題】\n")
            if (bestCards.isEmpty()) sb.append("データなし\n")
            bestCards.forEach { sb.append("・${it.first.question}（直近正解 ${it.second}回）\n") }

            rankingText.text = sb.toString()
            categoryText.text = targetCategory
        }

        updateRankingText()

        prevButton.setOnClickListener {
            currentCategoryIndex = if (currentCategoryIndex - 1 < 0) categoryList.size - 1 else currentCategoryIndex - 1
            updateRankingText()
        }
        nextButton.setOnClickListener {
            currentCategoryIndex = if (currentCategoryIndex + 1 >= categoryList.size) 0 else currentCategoryIndex + 1
            updateRankingText()
        }

        AlertDialog.Builder(context)
            .setTitle("学習ハイライト")
            .setView(layout)
            .setPositiveButton("閉じる", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.switchBgm(R.raw.contedefees_vo_0028)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}