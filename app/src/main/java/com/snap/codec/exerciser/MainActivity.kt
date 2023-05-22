package com.snap.codec.exerciser

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import com.snap.codec.exerciser.content.ContentManager
import java.util.Collections
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : ListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // One-time singleton initialization; requires activity context to get file location.
        ContentManager.initialize(this)
        listAdapter = SimpleAdapter(this, createActivityList(),
                android.R.layout.two_line_list_item, arrayOf(TITLE, DESCRIPTION), intArrayOf(android.R.id.text1, android.R.id.text2))

        val cm: ContentManager = ContentManager.instance
        if (!cm.isContentCreated()) {
            ContentManager.instance.createAll(this)
        }
    }

    /**
     * Creates the list of activities from the string arrays.
     */
    private fun createActivityList(): List<Map<String, Any>>? {
        val testList: MutableList<Map<String, Any>> = ArrayList()
        for (test in TESTS) {
            val tmp: MutableMap<String, Any> = HashMap()
            tmp[TITLE] = test[0]
            tmp[DESCRIPTION] = test[1]
            val intent = Intent()
            // Do the class name resolution here, so we crash up front rather than when the
            // activity list item is selected if the class name is wrong.
            try {
                val cls = Class.forName("com.snap.codec.exerciser." + test[2])
                intent.setClass(this, cls)
                tmp[CLASS_NAME] = intent
            } catch (cnfe: ClassNotFoundException) {
                throw RuntimeException("Unable to find " + test[2], cnfe)
            }
            testList.add(tmp)
        }
        Collections.sort(testList, TEST_LIST_COMPARATOR)
        return testList
    }

    override fun onListItemClick(
        listView: ListView,
        view: View?,
        position: Int,
        id: Long
    ) {
        val map =
            listView.getItemAtPosition(position) as Map<String, Any>
        val intent = map[CLASS_NAME] as Intent?
        startActivity(intent)
    }

    companion object {
        const val TAG = "CodecTester"

        // map keys
        private const val TITLE = "title"
        private const val DESCRIPTION = "description"
        private const val CLASS_NAME = "class_name"

        /**
         * Each entry has three strings: the test title, the test description, and the name of
         * the activity class.
         */
        private val TESTS = arrayOf(
            arrayOf("ExoPlayer simulation Test",
                "Stress test for codec reuse",
                "ExoPlayerSimulatorActivity"),
            arrayOf("ExoPlayer Test",
                "Stress test for ExoPlayer",
                "ExoPlayerActivity"),
            arrayOf("Surface Shuffling Test",
                "Quickly changes the surfaces on working player",
                "SurfaceShuffleActivity")
        )

        /**
         * Compares two list items.
         */
        @JvmStatic
        private val TEST_LIST_COMPARATOR = Comparator<Map<String, Any>> { map1, map2 ->
            val title1 = map1[TITLE] as String?
            val title2 = map2[TITLE] as String?
            title1!!.compareTo(title2!!)
        }
    }
}