package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MutableSpinnerItemsTest {
    @Test
    fun backingItemsCanBeClearedAndRepopulated() {
        val items = MutableSpinnerItems.copyOf(arrayOf("صورة واحدة.jpg", "مقطع.mp4"))

        items.clear()
        items.add("لا توجد وسائط بعد")

        assertEquals(listOf("لا توجد وسائط بعد"), items)
    }

    @Test
    fun nullSourceCreatesAnEmptyMutableList() {
        val items = MutableSpinnerItems.copyOf(null)

        items.add("لا توجد وسائط بعد")

        assertEquals(listOf("لا توجد وسائط بعد"), items)
    }
}
