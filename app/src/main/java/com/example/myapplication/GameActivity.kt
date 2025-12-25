package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class GameActivity : AppCompatActivity() {

    // Кнопки поля 3x3
    private lateinit var buttons: List<Button>

    // Кнопки управления и текст результата
    private lateinit var resetButton: Button
    private lateinit var backButton: Button
    private lateinit var gameResult: TextView

    // Иконки игрока и бота
    private lateinit var playerIcon: ImageView
    private lateinit var botIcon: ImageView

    // Состояние игры
    private var isPlayerTurn = true
    private var isGameOver = false

    private val handler = Handler(Looper.getMainLooper())

    // Очереди ходов: храним индексы клеток
    private val playerMoves = ArrayDeque<Int>()
    private val aiMoves = ArrayDeque<Int>()
    private val maxMarksPerPlayer = 3

    // Индексы мигающих фишек
    private var blinkingPlayerIndex: Int? = null
    private var blinkingAiIndex: Int? = null

    // Клетка, из которой ИИ только что удалил свою фишку
    private var lastRemovedAiIndex: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Находим все элементы по id
        buttons = listOf(
            findViewById(R.id.button_1),
            findViewById(R.id.button_2),
            findViewById(R.id.button_3),
            findViewById(R.id.button_4),
            findViewById(R.id.button_5),
            findViewById(R.id.button_6),
            findViewById(R.id.button_7),
            findViewById(R.id.button_8),
            findViewById(R.id.button_9)
        )

        resetButton = findViewById(R.id.reset_button)
        backButton = findViewById(R.id.back_button)
        gameResult = findViewById(R.id.game_result)

        playerIcon = findViewById(R.id.player_icon)
        botIcon = findViewById(R.id.bot_icon)

        // Обработчики нажатий на клетки
        buttons.forEach { btn ->
            btn.setOnClickListener {
                if (isPlayerTurn && !isGameOver && btn.text.isEmpty()) {
                    onPlayerMove(btn)
                }
            }
        }

        // Кнопки "Заново" и "Назад"
        resetButton.setOnClickListener { resetGame() }
        backButton.setOnClickListener { finish() }

        resetGame()
    }

    // ------------------------------------------------
    //               ПОДСВЕТКА / МИГАНИЕ
    // ------------------------------------------------

    // Подсветка только что поставленной фишки (зелёный на 2 секунды)
    private fun highlightNewMove(index: Int) {
        val button = buttons[index]
        button.setTextColor(Color.parseColor("#00FF00"))
        handler.postDelayed({
            // вернуть обычный цвет
            button.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
            )
        }, 2000)
    }

    // Запуск мигания для кнопки
    private fun startBlink(index: Int) {
        val button = buttons[index]
        val anim = AlphaAnimation(1.0f, 0.3f)
        anim.duration = 500
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        button.startAnimation(anim)
    }

    private fun clearBlinkForPlayer() {
        blinkingPlayerIndex?.let { idx ->
            val btn = buttons[idx]
            btn.clearAnimation()
            btn.alpha = 1f
        }
        blinkingPlayerIndex = null
    }

    private fun clearBlinkForAi() {
        blinkingAiIndex?.let { idx ->
            val btn = buttons[idx]
            btn.clearAnimation()
            btn.alpha = 1f
        }
        blinkingAiIndex = null
    }

    // Начать мигание старейшей фишки игрока, если их уже maxMarksPerPlayer
    private fun startBlinkForPlayerIfNeeded() {
        clearBlinkForPlayer()
        if (playerMoves.size == maxMarksPerPlayer) {
            val idx = playerMoves.first()
            blinkingPlayerIndex = idx
            startBlink(idx)
        }
    }

    // Аналогично для ИИ
    private fun startBlinkForAiIfNeeded() {
        clearBlinkForAi()
        if (aiMoves.size == maxMarksPerPlayer) {
            val idx = aiMoves.first()
            blinkingAiIndex = idx
            startBlink(idx)
        }
    }

    // Очистить клетку полностью
    private fun clearCell(index: Int) {
        val button = buttons[index]
        button.text = ""
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        button.clearAnimation()
        button.alpha = 1f
    }

    // ------------------------------------------------
    //            ИКОНКИ ХОДА (ИГРОК / БОТ)
    // ------------------------------------------------

    private fun showPlayerTurnUI() {
        // игрок активен (зелёный), бот пассивен (красный)
        playerIcon.setImageResource(R.drawable.player_green)
        botIcon.setImageResource(R.drawable.bot_red)
    }

    private fun showAiTurnUI() {
        // бот активен (зелёный), игрок пассивен (синий)
        playerIcon.setImageResource(R.drawable.player_blue)
        botIcon.setImageResource(R.drawable.bot_green)
    }

    // ------------------------------------------------
    //                  ХОД ИГРОКА
    // ------------------------------------------------

    private fun onPlayerMove(button: Button) {
        if (!isPlayerTurn || isGameOver || button.text.isNotEmpty()) return

        // Если у игрока уже 3 фишки – старая уже мигала, теперь удаляем её
        if (playerMoves.size >= maxMarksPerPlayer) {
            val oldIndex = playerMoves.removeFirst()
            clearBlinkForPlayer()
            clearCell(oldIndex)
        }

        // Ставим новый крестик
        button.text = "X"
        val newIndex = buttons.indexOf(button)
        playerMoves.addLast(newIndex)
        highlightNewMove(newIndex)

        // Проверка победы
        if (checkGameState()) return

        // Передаём ход ИИ
        switchToAI()
    }

    private fun switchToAI() {
        isPlayerTurn = false
        showAiTurnUI()

        // При начале хода ИИ, если у него уже 3 фишки – старейшая мигает
        startBlinkForAiIfNeeded()

        handler.postDelayed({
            if (!isGameOver) {
                makeAIMove()
            }
        }, 700)
    }

    // ------------------------------------------------
    //                      ХОД ИИ
    // ------------------------------------------------

    private fun makeAIMove() {
        if (isGameOver) return

        // Если у ИИ уже 3 фишки – одна из них мигала, теперь удаляем её
        if (aiMoves.size >= maxMarksPerPlayer) {
            val oldIndex = aiMoves.removeFirst()
            clearBlinkForAi()
            clearCell(oldIndex)

            // Запомнить, откуда удалили, чтобы в этот же ход туда не ходить
            lastRemovedAiIndex = oldIndex
        } else {
            lastRemovedAiIndex = null
        }

        performAIMove()
    }

    private fun performAIMove() {
        if (isGameOver) return

        // Список пустых клеток, кроме только что очищенной
        val emptyCells = buttons.indices.filter { idx ->
            buttons[idx].text.isEmpty() && idx != lastRemovedAiIndex
        }.toMutableList()

        if (emptyCells.isEmpty()) {
            // Нет доступных клеток – просто передаём ход игроку
            isPlayerTurn = true
            showPlayerTurnUI()
            startBlinkForPlayerIfNeeded()
            return
        }

        val winIndex = findBestMoveFor("O")
        val blockIndex = findBestMoveFor("X")

        val moveIndex = when {
            winIndex != null -> winIndex
            blockIndex != null -> blockIndex
            else -> emptyCells.random()
        }

        if (buttons[moveIndex].text.isEmpty()) {
            buttons[moveIndex].text = "O"
            aiMoves.addLast(moveIndex)
            highlightNewMove(moveIndex)
            lastRemovedAiIndex = null
        }

        if (checkGameState()) return

        // Ход переходит игроку
        isPlayerTurn = true
        showPlayerTurnUI()
        startBlinkForPlayerIfNeeded()
    }

    // ------------------------------------------------
    //                  ЛОГИКА ИИ
    // ------------------------------------------------

    // Ищем клетку, где можно выиграть (symbol = "O") или заблокировать (symbol = "X")
    private fun findBestMoveFor(symbol: String): Int? {
        val winningCombinations = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(0, 3, 6),
            listOf(1, 4, 7),
            listOf(2, 5, 8),
            listOf(0, 4, 8),
            listOf(2, 4, 6)
        )

        for (comb in winningCombinations) {
            val a = comb[0]
            val b = comb[1]
            val c = comb[2]

            val texts = listOf(
                buttons[a].text.toString(),
                buttons[b].text.toString(),
                buttons[c].text.toString()
            )

            val countSymbol = texts.count { it == symbol }
            val countEmpty = texts.count { it.isEmpty() }

            if (countSymbol == 2 && countEmpty == 1) {
                val emptyIndexInCombo = texts.indexOfFirst { it.isEmpty() }
                val idx = comb[emptyIndexInCombo]
                // На всякий случай не ходим в только что очищенную клетку
                if (idx == lastRemovedAiIndex) continue
                return idx
            }
        }

        return null
    }

    // ------------------------------------------------
    //                ПРОВЕРКА ПОБЕДЫ
    // ------------------------------------------------

    private fun checkGameState(): Boolean {
        val winningCombinations = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(0, 3, 6),
            listOf(1, 4, 7),
            listOf(2, 5, 8),
            listOf(0, 4, 8),
            listOf(2, 4, 6)
        )

        for (combination in winningCombinations) {
            val (a, b, c) = combination
            if (buttons[a].text.isNotEmpty() &&
                buttons[a].text == buttons[b].text &&
                buttons[a].text == buttons[c].text
            ) {
                highlightWinningCombination(combination)
                val winner = buttons[a].text.toString()
                showGameResult(winner)
                return true
            }
        }

        return false
    }

    private fun highlightWinningCombination(combination: List<Int>) {
        val lightRedColor = Color.parseColor("#FF6666")
        combination.forEach { index ->
            buttons[index].setTextColor(lightRedColor)
        }
    }

    private fun showGameResult(winner: String) {
        gameResult.visibility = View.VISIBLE
        gameResult.text = if (winner == "X") "Победили крестики!" else "Победили нолики!"
        clearBlinkForPlayer()
        clearBlinkForAi()
        isGameOver = true
    }

    // ------------------------------------------------
    //           СБРОС И РАНДОМ ВЫБОРА ПЕРВОГО
    // ------------------------------------------------

    private fun resetGame() {
        buttons.forEach {
            it.text = ""
            it.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            it.clearAnimation()
            it.alpha = 1f
        }

        gameResult.visibility = View.GONE
        isGameOver = false
        playerMoves.clear()
        aiMoves.clear()
        clearBlinkForPlayer()
        clearBlinkForAi()
        lastRemovedAiIndex = null

        startRandomTurn()
    }

    private fun startRandomTurn() {
        val playerStarts = (0..1).random() == 0

        if (playerStarts) {
            isPlayerTurn = true
            showPlayerTurnUI()
            startBlinkForPlayerIfNeeded()
        } else {
            isPlayerTurn = false
            showAiTurnUI()
            startBlinkForAiIfNeeded()
            handler.postDelayed({
                if (!isGameOver) {
                    makeAIMove()
                }
            }, 700)
        }
    }
}