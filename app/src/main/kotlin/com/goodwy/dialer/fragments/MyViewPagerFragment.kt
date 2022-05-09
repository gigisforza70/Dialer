package com.goodwy.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.getAdjustedPrimaryColor
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.Config
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import kotlinx.android.synthetic.main.fragment_recents.view.*

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null

    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
            setupColors(config.textColor, config.primaryColor, activity.getAdjustedPrimaryColor())
        }
    }

    fun finishActMode() {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (recents_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, adjustedPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)
}
