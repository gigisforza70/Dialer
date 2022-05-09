package com.goodwy.dialer.activities

import android.animation.Animator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.SimpleContact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.extensions.addCharacter
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getKeyEvent
import com.goodwy.dialer.extensions.startCallIntent
import com.goodwy.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.activity_dialpad.*
import kotlinx.android.synthetic.main.activity_dialpad.dialpad_holder
import kotlinx.android.synthetic.main.dialpad.*
import java.util.*
import kotlin.collections.ArrayList
import android.animation.AnimatorListenerAdapter
import androidx.recyclerview.widget.RecyclerView


class DialpadActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private val russianCharsMap = HashMap<Char, Int>()
    private var hasRussianLocale = false
    private var privateCursor: Cursor? = null
    private var hasBeenScrolled = false

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialpad)
        hasRussianLocale = Locale.getDefault().language == "ru"

        if (checkAppSideloading()) {
            return
        }

        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(false, true)?.loadInBackground()

        val fontSize = getTextSize() - 8f//resources.getDimension(R.dimen.small_text_size)
        arrayOf(
            dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
            dialpad_9_letters
        ).forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
        }

        if (hasRussianLocale) {
            initRussianChars()
            dialpad_2_letters.append("\nАБВГ")
            dialpad_3_letters.append("\nДЕЁЖЗ")
            dialpad_4_letters.append("\nИЙКЛ")
            dialpad_5_letters.append("\nМНОП")
            dialpad_6_letters.append("\nРСТУ")
            dialpad_7_letters.append("\nФХЦЧ")
            dialpad_8_letters.append("\nШЩЪЫ")
            dialpad_9_letters.append("\nЬЭЮЯ")

            val fontSizeRu = getTextSize() - 12f//resources.getDimension(R.dimen.small_text_size)
            arrayOf(
                dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
                dialpad_9_letters
            ).forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
            }
        }

        dialpad_0_holder.setOnClickListener { dialpadPressed('0', it) }
        dialpad_1_holder.setOnClickListener { dialpadPressed('1', it) }
        dialpad_2_holder.setOnClickListener { dialpadPressed('2', it) }
        dialpad_3_holder.setOnClickListener { dialpadPressed('3', it) }
        dialpad_4_holder.setOnClickListener { dialpadPressed('4', it) }
        dialpad_5_holder.setOnClickListener { dialpadPressed('5', it) }
        dialpad_6_holder.setOnClickListener { dialpadPressed('6', it) }
        dialpad_7_holder.setOnClickListener { dialpadPressed('7', it) }
        dialpad_8_holder.setOnClickListener { dialpadPressed('8', it) }
        dialpad_9_holder.setOnClickListener { dialpadPressed('9', it) }

        dialpad_1_holder.setOnLongClickListener { speedDial(1); true }
        dialpad_2_holder.setOnLongClickListener { speedDial(2); true }
        dialpad_3_holder.setOnLongClickListener { speedDial(3); true }
        dialpad_4_holder.setOnLongClickListener { speedDial(4); true }
        dialpad_5_holder.setOnLongClickListener { speedDial(5); true }
        dialpad_6_holder.setOnLongClickListener { speedDial(6); true }
        dialpad_7_holder.setOnLongClickListener { speedDial(7); true }
        dialpad_8_holder.setOnLongClickListener { speedDial(8); true }
        dialpad_9_holder.setOnLongClickListener { speedDial(9); true }

        dialpad_0_holder.setOnLongClickListener { dialpadPressed('+', null); true }
        dialpad_asterisk_holder.setOnClickListener { dialpadPressed('*', it) }
        dialpad_hashtag_holder.setOnClickListener { dialpadPressed('#', it) }
        dialpad_down_holder.setOnClickListener { dialpadHide() }
        dialpad_add_number.setOnClickListener { addNumberToContact() }
        dialpad_clear_char_holder.setOnClickListener { clearChar(it) }
        dialpad_clear_char_holder.setOnLongClickListener { clearInput(); true }
        dialpad_call_button_holder.setOnClickListener { initCall() }
        dialpad_call_button_holder.setOnLongClickListener {
            if (dialpad_input.value.isEmpty()) {dialpad_input.setText(getTextFromClipboard()); true}
            else {copyNumber(); true}
        }
        dialpad_input.onTextChangeListener { dialpadValueChanged(it) }
        SimpleContactsHelper(this).getAvailableContacts(false) { gotContacts(it) }
        disableKeyboardPopping()

        val adjustedPrimaryColor = getAdjustedPrimaryColor()
        val callIcon = resources.getColoredDrawableWithColor(R.drawable.ic_phone_vector, adjustedPrimaryColor.getContrastColor())
        dialpad_call_button.setImageDrawable(callIcon)
        dialpad_call_button.background.applyColorFilter(config.accentColor)

        letter_fastscroller.textColor = config.textColor.getColorStateList()
        letter_fastscroller.pressedTextColor = adjustedPrimaryColor
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = adjustedPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = adjustedPrimaryColor.getColorStateList()
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        updateTextColors(dialpad_holder)
        dialpad_wrapper.setBackgroundColor(config.backgroundColor)
        dialpad_buttom_bg.setBackgroundColor(config.backgroundColor)
        dialpad_add_number.setTextColor(config.primaryColor)
        dialpad_holder.setBackgroundColor(config.backgroundColor)
        dialpad_clear_char.applyColorFilter(config.textColor)
        dialpad_down.applyColorFilter(config.textColor)
        dialpad_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!hasBeenScrolled) {
                    hasBeenScrolled = true
                    slideDown(dialpad_wrapper)
                }
            }
        })
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dialpad, menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.add_number_to_contact -> addNumberToContact()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun copyNumber() {
        val clip = dialpad_input.value
        copyToClipboard(clip)
    }

    private fun checkDialIntent(): Boolean {
        return if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data != null && intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            dialpad_input.setText(number)
            dialpad_input.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, dialpad_input.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        dialpad_input.addCharacter(char)
        view?.performHapticFeedback()
    }

    private fun dialpadHide() {
        if (dialpad_wrapper.visibility == View.VISIBLE) {
            slideDown(dialpad_wrapper)
        } else {
            slideUp(dialpad_wrapper)
        }
    }

    fun slideDown(view: View) {
        view.animate()
            .translationY(view.height.toFloat())
            //.alpha(0f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    // superfluous restoration
                    view.visibility = View.GONE
                    //view.alpha = 1f
                    view.translationY = 0f
                }
            })
        hasBeenScrolled = false
        dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_up_vector))
    }

    fun slideUp(view: View) {
        view.visibility = View.VISIBLE
        //view.alpha = 0f
        if (view.height > 0) {
            slideUpNow(view)
        } else {
            // wait till height is measured
            view.post { slideUpNow(view) }
        }
        dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_down_vector))
    }

    private fun slideUpNow(view: View) {
        view.translationY = view.height.toFloat()
        view.animate()
            .translationY(0f)
            //.alpha(1f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.VISIBLE
                    //view.alpha = 1f
                }
            })
    }

    private fun clearChar(view: View) {
        dialpad_input.dispatchKeyEvent(dialpad_input.getKeyEvent(KeyEvent.KEYCODE_DEL))
        view.performHapticFeedback()
    }

    private fun clearInput() {
        dialpad_input.setText("")
    }

    private fun disableKeyboardPopping() {
        dialpad_input.showSoftInputOnFocus = false
    }

    private fun gotContacts(newContacts: ArrayList<SimpleContact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && dialpad_input.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len == 0 && dialpad_wrapper.visibility == View.GONE) {
            slideUp(dialpad_wrapper)
            dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_down_vector))
        }
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent(SECRET_CODE_ACTION, Uri.parse("android_secret_code://$secretCode"))
                sendBroadcast(intent)
            }
            return
        }

        (dialpad_list.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter {
            var convertedName = PhoneNumberUtils.convertKeypadLettersToDigits(it.name.normalizeString())

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.toLowerCase().forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName
            }

            it.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as ArrayList<SimpleContact>

        letter_fastscroller.setupWithRecyclerView(dialpad_list, { position ->
            try {
                val name = filtered[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        ContactsAdapter(this, filtered, dialpad_list, null, text, showNumber = true) {
            //startCallIntent((it as SimpleContact).phoneNumbers.first())
            val phoneNumbers = (it as SimpleContact).phoneNumbers
            if (phoneNumbers.size <= 1) {
                launchCallIntent(phoneNumbers.first())
            } else {
                val items = java.util.ArrayList<RadioItem>()
                phoneNumbers.forEachIndexed { index, phoneNumber ->
                    items.add(RadioItem(index, phoneNumber))
                }

                RadioGroupDialog(this, items) {
                    launchCallIntent(phoneNumbers[it as Int])
                }
            }
        }.apply {
            dialpad_list.adapter = this
        }

        dialpad_add_number.beVisibleIf(!dialpad_input.value.isEmpty())
        dialpad_add_number.setTextColor(config.primaryColor)
        dialpad_placeholder.beVisibleIf(filtered.isEmpty())
        dialpad_list.beVisibleIf(filtered.isNotEmpty())
        dialpad_down_holder.beVisibleIf(!dialpad_input.value.isEmpty())
        dialpad_clear_char_holder.beVisibleIf(!dialpad_input.value.isEmpty())
        dialpad_input.beVisibleIf(!dialpad_input.value.isEmpty())
        //dialpad_wrapper.beVisibleIf(dialpad_input.value.isEmpty())
        dialpad_list.beVisibleIf(!dialpad_input.value.isEmpty())
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(dialpad_input.value)
        }
    }

    private fun initCall(number: String = dialpad_input.value) {
        if (number.isNotEmpty()) {
            startCallIntent(number)
        }
    }

    private fun speedDial(id: Int) {
        if (dialpad_input.value.isEmpty()) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number)
            }
        }
    }

    private fun initRussianChars() {
        russianCharsMap['а'] = 2; russianCharsMap['б'] = 2; russianCharsMap['в'] = 2; russianCharsMap['г'] = 2
        russianCharsMap['д'] = 3; russianCharsMap['е'] = 3; russianCharsMap['ё'] = 3; russianCharsMap['ж'] = 3; russianCharsMap['з'] = 3
        russianCharsMap['и'] = 4; russianCharsMap['й'] = 4; russianCharsMap['к'] = 4; russianCharsMap['л'] = 4
        russianCharsMap['м'] = 5; russianCharsMap['н'] = 5; russianCharsMap['о'] = 5; russianCharsMap['п'] = 5
        russianCharsMap['р'] = 6; russianCharsMap['с'] = 6; russianCharsMap['т'] = 6; russianCharsMap['у'] = 6
        russianCharsMap['ф'] = 7; russianCharsMap['х'] = 7; russianCharsMap['ц'] = 7; russianCharsMap['ч'] = 7
        russianCharsMap['ш'] = 8; russianCharsMap['щ'] = 8; russianCharsMap['ъ'] = 8; russianCharsMap['ы'] = 8
        russianCharsMap['ь'] = 9; russianCharsMap['э'] = 9; russianCharsMap['ю'] = 9; russianCharsMap['я'] = 9
    }
}
