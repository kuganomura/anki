package com.example.anki

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anki.databinding.FragmentSecondBinding
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var cardList: List<Card> = emptyList()
    private var currentIndex = 0
    private var timer: CountDownTimer? = null

    private var isDaily = false
    private var dailyCorrectCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        val targetTag = arguments?.getString("SELECTED_TAG") ?: "すべて"
        isDaily = arguments?.getBoolean("IS_DAILY", false) ?: false

        executor.execute {
            val allCards = db.cardDao().allCards

            val filteredCards = if (isDaily) {
                allCards.shuffled().take(3)
            } else if (targetTag == "すべて") {
                allCards
            } else {
                allCards.filter { it.category == targetTag }
            }

            cardList = filteredCards.shuffled()

            handler.post {
                if (cardList.isNotEmpty()) {
                    showQuestion()
                }
            }
        }

        binding.buttonSubmitAnswer.setOnClickListener {
            checkAnswer(isTimeUp = false)
        }

        binding.editAnswer.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                checkAnswer(isTimeUp = false)
                true
            } else {
                false
            }
        }
    }

    // ★追加：暗記画面に入った時に、モードに応じてBGMを自動で切り替える
    override fun onResume() {
        super.onResume()
        if (isDaily) {
            // デイリーテスト用のBGM（res/raw/bgm_daily.mp3 を想定）
            (activity as? MainActivity)?.switchBgm(R.raw.dbgm)
        } else {
            // 通常の暗記用のBGM（res/raw/bgm_normal.mp3 を想定）
            (activity as? MainActivity)?.switchBgm(R.raw.bgm)
        }
    }

    private fun showQuestion() {
        val currentCard = cardList[currentIndex]

        val modeText = if (isDaily) "[デイリーテスト] " else ""
        binding.textQuestionNumber.text = "${modeText}問題 ${currentIndex + 1}/${cardList.size}"
        binding.textQuestion.text = "[${currentCard.category}]\n${currentCard.question}"
        binding.editAnswer.text.clear()

        binding.editAnswer.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.editAnswer, InputMethodManager.SHOW_IMPLICIT)

        if (isDaily) {
            binding.textTimer.visibility = View.VISIBLE
            startTimer(15000)
        } else if (currentCard.lastResult == 1) {
            binding.textTimer.visibility = View.VISIBLE
            startTimer(7000)
        } else {
            timer?.cancel()
            binding.textTimer.visibility = View.INVISIBLE
        }
    }

    private fun startTimer(duration: Long) {
        timer?.cancel()

        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.textTimer.text = secondsLeft.toString()
            }

            override fun onFinish() {
                binding.textTimer.text = "0"
                checkAnswer(isTimeUp = true)
            }
        }.start()
    }

    private fun checkAnswer(isTimeUp: Boolean) {
        timer?.cancel()

        val currentCard = cardList[currentIndex]
        val userAnswer = binding.editAnswer.text.toString()

        val normalizedUser = Normalizer.normalize(userAnswer, Normalizer.Form.NFKC).trim()
        val normalizedCorrect = Normalizer.normalize(currentCard.answer, Normalizer.Form.NFKC).trim()

        if (isTimeUp) {
            Toast.makeText(requireContext(), "時間切れ！正解は「${currentCard.answer}」", Toast.LENGTH_SHORT).show()
            currentCard.lastResult = 2
            currentCard.incorrectCount++
            updateRecentResults(currentCard, false)
        } else {
            if (normalizedUser == normalizedCorrect) {
                Toast.makeText(requireContext(), "正解！", Toast.LENGTH_SHORT).show()
                currentCard.lastResult = 1
                currentCard.correctCount++
                updateRecentResults(currentCard, true)
                if (isDaily) dailyCorrectCount++
            } else {
                Toast.makeText(requireContext(), "不正解... 正解は「${currentCard.answer}」", Toast.LENGTH_SHORT).show()
                currentCard.lastResult = 2
                currentCard.incorrectCount++
                updateRecentResults(currentCard, false)
            }
        }

        executor.execute {
            db.cardDao().update(currentCard)
            handler.post {
                currentIndex++
                if (currentIndex < cardList.size) {
                    showQuestion()
                } else {
                    val prefs = requireContext().getSharedPreferences("AnkiPrefs", Context.MODE_PRIVATE)

                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.editAnswer.windowToken, 0)

                    if (isDaily) {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val resultMark = if (dailyCorrectCount == 3) "STAR" else "CIRCLE"

                        prefs.edit()
                            .putString("DAILY_LAST_DATE", today)
                            .putString("DAILY_RESULT", resultMark)
                            .putString("DAILY_RESULT_$today", resultMark)
                            .apply()

                        val msg = if (dailyCorrectCount == 3) "🎉 全問正解！カレンダーに⭐がつきました！" else "デイリー完了！明日は全問正解を目指そう！"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    } else {
                        val playCount = prefs.getInt("PLAY_COUNT", 0) + 1
                        if (playCount % 5 == 0) {
                            Toast.makeText(requireContext(), "🎉 テスト5回達成！ハイライトが更新されました！", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireContext(), "テスト終了！お疲れ様でした", Toast.LENGTH_SHORT).show()
                        }
                        prefs.edit().putInt("PLAY_COUNT", playCount).apply()
                    }

                    findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
                }
            }
        }
    }

    private fun updateRecentResults(card: Card, isCorrect: Boolean) {
        val resultStr = if (isCorrect) "1" else "0"

        val currentResults = if (card.recentResults.isNullOrEmpty()) emptyList<String>() else card.recentResults.split(",")
        val newResults = currentResults.toMutableList()

        newResults.add(resultStr)

        if (newResults.size > 3) {
            newResults.removeAt(0)
        }

        card.recentResults = newResults.joinToString(",")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }
}