package com.example.babyallergycheck.data

data class Phase(
    val code: String,
    val label: String,
    val ageLabel: String,
)

data class FoodCategory(
    val code: String,
    val label: String,
)

object BabyMasters {
    val phases = listOf(
        Phase("01", "初期", "5～6か月"),
        Phase("02", "中期", "7～8か月"),
        Phase("03", "後期", "9～11か月"),
    )

    val categories = listOf(
        FoodCategory("01", "穀類"),
        FoodCategory("02", "野菜"),
        FoodCategory("03", "果物"),
        FoodCategory("04", "魚介類"),
        FoodCategory("05", "肉類"),
        FoodCategory("06", "大豆類"),
        FoodCategory("07", "調味料"),
        FoodCategory("08", "乳製品"),
        FoodCategory("09", "その他"),
    )

    fun phaseLabel(code: String): String =
        phases.firstOrNull { it.code == code }?.let { "${it.ageLabel}（${it.label}）" } ?: code

    fun shortPhaseLabel(code: String): String =
        phases.firstOrNull { it.code == code }?.label ?: code

    fun categoryLabel(code: String): String =
        categories.firstOrNull { it.code == code }?.label ?: code

    fun codeFor(phaseCode: String, categoryCode: String, serial: Int): String =
        phaseCode + categoryCode + serial.toString().padStart(3, '0')

    val defaultFoods = buildList {
        addFoods("01", "01", listOf("米", "うどん", "そうめん", "お麩", "パン(卵不使用品可)"))
        addFoods(
            "01",
            "02",
            listOf("人参", "玉ねぎ", "じゃがいも", "大根", "小松菜", "ほうれん草", "チンゲンサイ", "とまと", "かぶ", "かぼちゃ", "キャベツ", "白菜", "さつまいも"),
        )
        addFoods("01", "04", listOf("たら", "かれい"))
        addFoods("01", "06", listOf("豆腐"))
        addFoods("01", "09", listOf("粉ミルク", "昆布だし", "麦茶"))

        addFoods("02", "01", listOf("スパゲティ", "マカロニ", "パン粉"))
        addFoods("02", "02", listOf("きゅうり", "なす", "ブロッコリー", "わかめ", "さやいんげん"))
        addFoods("02", "03", listOf("りんご", "バナナ"))
        addFoods("02", "05", listOf("鶏肉(胸・ささみ)", "鶏ひき肉"))
        addFoods("02", "04", listOf("鮭", "ツナ(水煮缶)", "しらす干し", "かつお節"))
        addFoods("02", "06", listOf("大豆(水煮缶)", "高野豆腐", "きなこ", "豆乳"))
        addFoods("02", "08", listOf("チーズ", "バター", "牛乳(調理用)"))
        addFoods("02", "07", listOf("砂糖", "塩", "味噌", "しょうゆ", "片栗粉", "ケチャップ"))
        addFoods("02", "09", listOf("サラダ油", "焼きのり・青のり", "寒天"))
    }
}

private fun MutableList<FoodEntity>.addFoods(
    phaseCode: String,
    categoryCode: String,
    names: List<String>,
) {
    names.forEachIndexed { index, name ->
        val serial = index + 1
        add(
            FoodEntity(
                code = BabyMasters.codeFor(phaseCode, categoryCode, serial),
                phaseCode = phaseCode,
                categoryCode = categoryCode,
                serial = serial,
                name = name,
            ),
        )
    }
}
