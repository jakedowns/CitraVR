package org.citra.citra_emu.vr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.citra.citra_emu.R
import org.citra.citra_emu.applets.SoftwareKeyboard
import org.citra.citra_emu.applets.SoftwareKeyboard.KeyboardConfig
import org.citra.citra_emu.applets.SoftwareKeyboard.onFinishVrKeyboardNegative
import org.citra.citra_emu.applets.SoftwareKeyboard.onFinishVrKeyboardNeutral
import org.citra.citra_emu.applets.SoftwareKeyboard.onFinishVrKeyboardPositive
import org.citra.citra_emu.utils.Log.error
import org.citra.citra_emu.utils.Log.warning
import java.io.Serializable
import java.util.Locale

class VrKeyboardActivity : Activity() {

    // Result sent to the (Citra) software keyboard.
    class Result : Serializable {
        enum class Type {
            None,
            Positive,
            Neutral,
            Negative
        }

        constructor() {
            text = ""
            type = Type.None
            config = null
        }

        constructor(
            text: String, type: Type,
            config: KeyboardConfig?
        ) {
            this.text = text
            this.type = type
            this.config = config
        }

        constructor(type: Type) {
            text = ""
            this.type = type
            config = null
        }

        var text: String
        var type: Type
        var config: KeyboardConfig?
    }

    class Contract : ActivityResultContract<KeyboardConfig?, Result>() {
        override fun createIntent(context: Context, config: KeyboardConfig?): Intent {
            val intent = Intent(context, VrKeyboardActivity::class.java)
            intent.putExtra(EXTRA_KEYBOARD_INPUT_CONFIG, config)
            return intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Result {
            if (resultCode != RESULT_OK) {
                warning("parseResult(): Unexpected result code: $resultCode")
                return Result()
            }
            if (intent != null) {
                val result = intent.getSerializableExtra(EXTRA_KEYBOARD_RESULT) as Result?
                if (result != null) {
                    return result
                }
            }
            warning("parseResult(): finished with OK, but no result. Intent: $intent")
            return Result()
        }
    }

    private enum class KeyboardType {
        None,
        Abc,
        Num
    }

    private var mEditText: EditText? = null
    private var mIsShifted = false
    private var mKeyboardTypeCur = KeyboardType.None
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        var config: KeyboardConfig? = KeyboardConfig()
        if (extras != null) {
            config = extras.getSerializable(
                EXTRA_KEYBOARD_INPUT_CONFIG
            ) as KeyboardConfig?
        }
        setContentView(R.layout.vr_keyboard)
        mEditText = findViewById(R.id.vrKeyboardText)
        mEditText!!.apply {
            setHint(config!!.hintText)
            setSingleLine(!config.multilineMode)
            setFilters(
                arrayOf(
                    SoftwareKeyboard.Filter(),
                    LengthFilter(config.maxTextLength)
                )
            )
            // Needed to show cursor onscreen.
            requestFocus()
            WindowCompat.getInsetsController(window, this)
                .show(WindowInsetsCompat.Type.ime())
        }

        setupResultButtons(config)
        showKeyboardType(KeyboardType.Abc)
    }

    // Finish the activity when it loses focus, like an AlertDialog.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            finish()
        }
    }

    private fun setupResultButtons(config: KeyboardConfig?) {
        // Configure the result buttons
        findViewById<View>(R.id.keyPositive).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val resultIntent = Intent()
                resultIntent.putExtra(
                    EXTRA_KEYBOARD_RESULT,
                    Result(mEditText!!.text.toString(), Result.Type.Positive, config)
                )
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            false
        }
        findViewById<View>(R.id.keyNeutral).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_KEYBOARD_RESULT, Result(Result.Type.Neutral))
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            false
        }
        findViewById<View>(R.id.keyNegative).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_KEYBOARD_RESULT, Result(Result.Type.Negative))
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            false
        }
        when (config!!.buttonConfig) {
            SoftwareKeyboard.ButtonConfig.Triple -> {
                findViewById<View>(R.id.keyNeutral).visibility = View.VISIBLE
                findViewById<View>(R.id.keyNegative).visibility = View.VISIBLE
                findViewById<View>(R.id.keyPositive).visibility = View.VISIBLE
            }

            SoftwareKeyboard.ButtonConfig.Dual -> {
                findViewById<View>(R.id.keyNegative).visibility = View.VISIBLE
                findViewById<View>(R.id.keyPositive).visibility = View.VISIBLE
            }

            SoftwareKeyboard.ButtonConfig.Single -> findViewById<View>(R.id.keyPositive).visibility =
                View.VISIBLE

            SoftwareKeyboard.ButtonConfig.None -> {}
            else -> {
                error("Unknown button config: " + config.buttonConfig)
                assert(false)
            }
        }
    }

    private fun showKeyboardType(keyboardType: KeyboardType) {
        if (mKeyboardTypeCur == keyboardType) {
            return
        }
        mKeyboardTypeCur = keyboardType
        val keyboard = findViewById<ViewGroup>(R.id.vr_keyboard_keyboard)
        keyboard.removeAllViews()
        when (keyboardType) {
            KeyboardType.Abc -> {
                layoutInflater.inflate(R.layout.vr_keyboard_abc, keyboard)
                addLetterKeyHandlersForViewGroup(keyboard, mIsShifted)
            }

            KeyboardType.Num -> {
                layoutInflater.inflate(R.layout.vr_keyboard_123, keyboard)
                addLetterKeyHandlersForViewGroup(keyboard, false)
            }

            else -> assert(false)
        }
        addModifierKeyHandlers()
    }

    private fun addModifierKeyHandlers() {
        findViewById<View>(R.id.keyShift).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                setKeyCase(!mIsShifted)
            }
            false
        }
        // Note: I prefer touch listeners over click listeners because they activate
        // on the press instead of the release and therefore feel more responsive.
        findViewById<View>(R.id.keyBackspace).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val text = mEditText!!.text.toString()
                if (text.length > 0) {
                    // Delete character before cursor
                    val position = mEditText!!.selectionStart
                    if (position > 0) {
                        val newText = text.substring(0, position - 1) + text.substring(position)
                        mEditText!!.setText(newText)
                        mEditText!!.setSelection(position - 1)
                    }
                }
            }
            false
        }
        findViewById<View>(R.id.keySpace).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val position = mEditText!!.selectionStart
                if (position < mEditText!!.text.length) {
                    val newText = mEditText!!.text.toString().substring(0, position) + " " +
                            mEditText!!.text.toString().substring(position)
                    mEditText!!.setText(newText)
                    mEditText!!.setSelection(position + 1)
                } else {
                    mEditText!!.append(" ")
                }
            }
            false
        }
        findViewById<View>(R.id.keyLeft).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val position = mEditText!!.selectionStart
                if (position > 0) {
                    mEditText!!.setSelection(position - 1)
                }
            }
            false
        }
        findViewById<View>(R.id.keyRight).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val position = mEditText!!.selectionStart
                if (position < mEditText!!.text.length) {
                    mEditText!!.setSelection(position + 1)
                }
            }
            false
        }
        if (findViewById<View?>(R.id.keyNumbers) != null) {
            findViewById<View>(R.id.keyNumbers).setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showKeyboardType(KeyboardType.Num)
                }
                false
            }
        }
        if (findViewById<View?>(R.id.keyAbc) != null) {
            findViewById<View>(R.id.keyAbc).setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showKeyboardType(KeyboardType.Abc)
                }
                false
            }
        }
    }

    private fun addLetterKeyHandlersForViewGroup(
        viewGroup: ViewGroup,
        isShifted: Boolean
    ) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                addLetterKeyHandlersForViewGroup(child, isShifted)
            } else if (child is Button) {
                if ("key_letter" == child.getTag()) {
                    val key = child
                    key.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            val position = mEditText!!.selectionStart
                            if (position < mEditText!!.text.length) {
                                val newText = mEditText!!.text.toString().substring(0, position) +
                                        key.text.toString() +
                                        mEditText!!.text.toString().substring(position)
                                mEditText!!.setText(newText)
                                mEditText!!.setSelection(position + 1)
                            } else {
                                mEditText!!.append(key.text.toString())
                            }
                        }
                        false
                    }
                    setKeyCaseForButton(key, isShifted)
                }
            }
        }
    }

    private fun setKeyCase(isShifted: Boolean) {
        mIsShifted = isShifted
        val layout = findViewById<ViewGroup>(R.id.vr_keyboard)
        setKeyCaseForViewGroup(layout, isShifted)
    }

    companion object {
        private const val EXTRA_KEYBOARD_INPUT_CONFIG =
            "org.citra.citra_emu.vr.KEYBOARD_INPUT_CONFIG"
        private const val EXTRA_KEYBOARD_RESULT = "org.citra.citra_emu.vr.KEYBOARD_RESULT"
        fun onFinishResult(result: Result) {
            when (result.type) {
                Result.Type.Positive -> onFinishVrKeyboardPositive(result.text, result.config!!)
                Result.Type.Neutral -> onFinishVrKeyboardNeutral()
                Result.Type.Negative, Result.Type.None -> onFinishVrKeyboardNegative()
            }
        }

        private fun setKeyCaseForViewGroup(viewGroup: ViewGroup, isShifted: Boolean) {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                if (child is ViewGroup) {
                    setKeyCaseForViewGroup(child, isShifted)
                } else if (child is Button && "key_letter" == child.getTag()) {
                    setKeyCaseForButton(child, isShifted)
                }
            }
        }

        private fun setKeyCaseForButton(button: Button, isShifted: Boolean) {
            val text = button.text.toString()
            if (isShifted) {
                button.text = text.uppercase(Locale.getDefault())
            } else {
                button.text = text.lowercase(Locale.getDefault())
            }
        }
    }
}
