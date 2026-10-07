package dev.magicthemer.fixture

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** QA-only icon pack: deliberately contains three icons and one missing drawable. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Magic Themer QA Pack\n\nThree original vector icons and one deliberately missing icon.\n\nOpen Magic Themer to test discovery, preview and export."
            textSize = 20f
            gravity = Gravity.CENTER
            val inset = (24 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
        })
    }
}
