package dev.magicthemer.icons

/**
 * Broad app kinds used to suggest related artwork when a pack has nothing named
 * after the app. A category suggestion is a look-alike, never an identity match,
 * so it always ranks below name and mapping matches.
 *
 * - [appWords] are whole words in an app's label or package that imply the kind.
 * - [appPrefixes] catch fused names such as "Busitalia"; [prefixExceptions] are
 *   longer words that share a prefix but mean something else ("business").
 * - [genericArt] are artwork words that depict the kind itself ("music", "bus").
 * - [brandArt] are well-known apps of that kind, whose icons are close stand-ins.
 *
 * Artwork words also include the words of the app packages a pack maps the
 * artwork to, so `com.spotify.music` makes Spotify's icon a music icon.
 */
enum class AppCategory(
    val label: String,
    val appWords: Set<String>,
    val genericArt: Set<String>,
    val brandArt: Set<String>,
    val appPrefixes: Set<String> = emptySet(),
    val prefixExceptions: Set<String> = emptySet(),
) {
    MUSIC(
        "Music",
        appWords = setOf("music", "musica", "musik", "musique", "audio", "song", "songs", "tune", "tunes", "radio",
            "podcast", "podcasts", "mp3", "fm", "lyrics", "playlist", "equalizer", "karaoke"),
        genericArt = setOf("music", "musica", "musik", "musique", "audio", "song", "songs", "tune", "tunes", "radio",
            "podcast", "podcasts", "mp3", "headphones", "headphone", "headset", "equalizer", "vinyl", "playlist",
            "melody", "speaker", "lyrics"),
        brandArt = setOf("spotify", "deezer", "tidal", "soundcloud", "shazam", "poweramp", "musicolet", "pandora",
            "napster", "qobuz", "audiomack", "anghami", "boomplay", "saavn", "jiosaavn", "gaana", "ytmusic",
            "retromusic", "phonograph", "namida", "innertune", "vimusic", "rimusic", "metrolist", "symfonium",
            "plexamp", "blackplayer", "pulsar", "auxio", "gonemad", "neutron", "musixmatch", "bandcamp", "audible",
            "castbox", "pocketcasts", "antennapod", "tunein", "iheartradio", "harmonoid", "oto", "simplemusic"),
        appPrefixes = setOf("music", "musik", "audio", "tune", "radio"),
    ),
    VIDEO(
        "Video",
        appWords = setOf("video", "videos", "movie", "movies", "film", "films", "cinema", "tv", "iptv", "stream",
            "streaming", "streams", "anime", "series", "shows", "trailer", "trailers", "nuvio", "stremio"),
        genericArt = setOf("video", "videos", "movie", "movies", "film", "films", "cinema", "tv", "iptv", "television",
            "stream", "streaming", "clapperboard", "camcorder", "anime", "theater", "theatre"),
        brandArt = setOf("netflix", "youtube", "vlc", "mxplayer", "plex", "kodi", "stremio", "jellyfin", "emby",
            "primevideo", "disney", "disneyplus", "hbo", "hbomax", "hulu", "crunchyroll", "twitch", "nplayer",
            "justplayer", "newpipe", "tubular", "infuse", "pluto", "plutotv", "tubi", "rakuten", "raiplay", "mubi",
            "dazn", "paramount", "peacock", "nova", "cinexplore", "letterboxd", "seal", "vimeo", "dailymotion",
            "mpv", "kmplayer", "xplayer", "aniyomi", "cloudstream", "syncler", "kinopoisk", "nuvio"),
        appPrefixes = setOf("video", "movie", "film", "cine", "stream"),
        prefixExceptions = setOf("cinema4d"),
    ),
    GAMES(
        "Games",
        appWords = setOf("game", "games", "gaming", "gamer", "emulator", "emu", "arcade", "steam", "controller",
            "gamepad", "console", "retro", "retroarch", "esports", "puzzle", "chess", "solitaire", "sudoku"),
        genericArt = setOf("game", "games", "gaming", "gamer", "gamepad", "controller", "joystick", "joypad",
            "console", "arcade", "emulator", "dice", "trophy", "esports"),
        brandArt = setOf("steam", "playstation", "xbox", "nintendo", "retroarch", "yuzu", "ppsspp", "citra",
            "aethersx2", "nethersx2", "winlator", "gamehub", "gamenative", "pojav", "pojavlauncher", "minecraft",
            "roblox", "duckstation", "mupen", "mupen64", "drastic", "lemuroid", "daijisho", "odin", "eden",
            "sudachi", "skyline", "vita3k", "moonlight", "chiaki", "parsec", "geforce", "gamepass", "epicgames",
            "itchio", "gog", "heroic", "lutris", "playnite", "steamlink", "remoteplay", "pubg", "fortnite",
            "genshin", "supercell", "gameloft", "playgames", "gamecenter", "gamebox", "gameassistant"),
        appPrefixes = setOf("game", "gaming", "emu", "retro"),
        prefixExceptions = setOf("emulsion", "retrograde", "retrofit", "retrospective"),
    ),
    TRANSPORT(
        "Transport",
        appWords = setOf("bus", "buses", "train", "trains", "rail", "railway", "railways", "metro", "tram", "transit",
            "transport", "transportation", "trasporti", "trasporto", "taxi", "cab", "ticket", "tickets", "biglietti",
            "biglietto", "treno", "treni", "subway", "mobility", "mobilita", "commute", "ferry", "coach", "tpl",
            "autobus", "pullman", "rideshare"),
        genericArt = setOf("bus", "train", "tram", "metro", "subway", "transit", "transport", "taxi", "railway",
            "rail", "ticket", "tickets", "station", "ferry", "coach", "trolley", "underground", "commute"),
        brandArt = setOf("trenitalia", "italo", "moovit", "citymapper", "uber", "lyft", "bolt", "freenow",
            "flixbus", "blablacar", "omio", "trainline", "sncf", "renfe", "atac", "gtt", "tper", "busitalia",
            "tfl", "mta", "oebb", "sbb", "cotral", "itabus", "marino", "arriva", "dbnavigator", "navigator",
            "ouigo", "trenord", "atm", "transitapp", "cabify", "grab", "didi", "ola", "careem", "gojek"),
        appPrefixes = setOf("bus", "tren", "train", "rail", "metro", "transit", "trasport", "transport", "taxi"),
        prefixExceptions = setOf("business", "businesses", "busy", "bust", "bustle", "trainer", "trainers",
            "training", "metronome", "metronomes", "metropolis", "metropolitan"),
    ),
    MAPS(
        "Maps",
        appWords = setOf("map", "maps", "navigation", "navigator", "navi", "gps", "nav", "route", "routes",
            "compass", "atlas", "traffic"),
        genericArt = setOf("map", "maps", "navigation", "navigator", "gps", "compass", "location", "route", "pin",
            "globe", "atlas"),
        brandArt = setOf("waze", "osmand", "organicmaps", "herewego", "sygic", "mapsme", "magicearth", "komoot",
            "googlemaps", "mapslite", "mapy", "tomtom", "gaia", "alltrails", "cometin", "coyote"),
        appPrefixes = setOf("map", "navi", "gps"),
        prefixExceptions = setOf("maple", "mapping"),
    ),
    PHOTO(
        "Photos and camera",
        appWords = setOf("photo", "photos", "camera", "cam", "gallery", "picture", "pictures", "pics", "image",
            "images", "selfie", "snap", "lens", "editor", "collage", "filters"),
        genericArt = setOf("photo", "photos", "camera", "gallery", "picture", "pictures", "image", "images",
            "selfie", "lens", "album", "photography", "polaroid"),
        brandArt = setOf("lightroom", "snapseed", "vsco", "picsart", "photoshop", "aves", "fossify", "gcam",
            "googlecamera", "opencamera", "pixlr", "canva", "facetune", "remini", "meitu", "instagram"),
        appPrefixes = setOf("photo", "camera", "galler", "pic"),
        prefixExceptions = setOf("picnic", "pick", "picker", "pickup"),
    ),
    CHAT(
        "Chat and social",
        appWords = setOf("chat", "chats", "messenger", "message", "messages", "messaging", "sms", "mms", "social",
            "talk", "dating", "forum", "community", "friends", "call", "calls", "voip"),
        genericArt = setOf("chat", "messenger", "message", "messages", "messaging", "sms", "bubble", "social",
            "talk", "conversation", "dating"),
        brandArt = setOf("whatsapp", "telegram", "signal", "messenger", "discord", "slack", "viber", "line",
            "wechat", "snapchat", "instagram", "facebook", "threads", "bluesky", "mastodon", "reddit", "twitter",
            "skype", "teams", "element", "matrix", "kakaotalk", "tinder", "bumble", "hinge", "textra", "qksms"),
        appPrefixes = setOf("chat", "messag"),
    ),
    BROWSER(
        "Browser",
        appWords = setOf("browser", "web", "internet", "browse", "surf"),
        genericArt = setOf("browser", "web", "internet", "globe", "www", "surf"),
        brandArt = setOf("chrome", "firefox", "fennec", "brave", "opera", "vivaldi", "edge", "duckduckgo",
            "kiwi", "samsunginternet", "mull", "cromite", "mulch", "thorium", "tor", "torbrowser", "ecosia",
            "iceraven", "fulguris", "lightning", "via", "quetta", "arc", "yandex"),
        appPrefixes = setOf("browser"),
    ),
    MAIL(
        "Mail",
        appWords = setOf("mail", "email", "inbox", "posta", "webmail"),
        genericArt = setOf("mail", "email", "inbox", "envelope", "letter", "posta"),
        brandArt = setOf("gmail", "outlook", "protonmail", "proton", "thunderbird", "k9", "fairemail", "spark",
            "aquamail", "bluemail", "tutanota", "tuta", "yahoo"),
        appPrefixes = setOf("mail", "email"),
    ),
    MONEY(
        "Banking and money",
        appWords = setOf("bank", "banking", "banca", "banco", "wallet", "pay", "payment", "payments", "money",
            "finance", "finances", "budget", "card", "cards", "invest", "investing", "trading", "crypto", "stocks",
            "exchange", "expense", "expenses", "credit", "loan", "insurance", "assicurazioni", "conto", "tax"),
        genericArt = setOf("bank", "banking", "wallet", "money", "finance", "budget", "card", "credit", "coin",
            "coins", "cash", "dollar", "euro", "crypto", "bitcoin", "stocks", "piggy", "pay"),
        brandArt = setOf("paypal", "revolut", "n26", "wise", "satispay", "intesa", "unicredit", "fineco",
            "postepay", "bancoposta", "hype", "bunq", "monzo", "binance", "coinbase", "kraken", "trading212",
            "etoro", "degiro", "scalable", "traderepublic", "googlepay", "gpay", "applepay", "curve", "klarna",
            "venmo", "cashapp", "zelle", "chase", "hsbc", "barclays", "ing", "bbva", "santander", "mediolanum"),
        appPrefixes = setOf("bank", "banca", "banco", "wallet", "money", "financ", "invest", "crypto"),
        prefixExceptions = setOf("bankrupt"),
    ),
    SHOPPING(
        "Shopping",
        appWords = setOf("shop", "shopping", "store", "market", "marketplace", "deals", "coupon", "coupons", "cart",
            "buy", "sell", "outlet", "fashion", "supermarket", "grocery", "groceries", "spesa", "offerte"),
        genericArt = setOf("shop", "shopping", "store", "market", "cart", "bag", "basket", "tag", "deals",
            "coupon", "receipt", "grocery"),
        brandArt = setOf("amazon", "ebay", "aliexpress", "temu", "shein", "zalando", "vinted", "subito",
            "etsy", "wish", "allegro", "walmart", "target", "ikea", "zara", "hm", "decathlon", "lidl", "aldi",
            "esselunga", "conad", "carrefour", "coop", "mediaworld", "unieuro", "wallapop", "depop"),
        appPrefixes = setOf("shop", "store", "market"),
        prefixExceptions = setOf("marketing"),
    ),
    FOOD(
        "Food and delivery",
        appWords = setOf("food", "foods", "delivery", "restaurant", "restaurants", "recipe", "recipes", "pizza",
            "burger", "kitchen", "cooking", "cook", "eat", "eats", "drink", "coffee", "cafe", "bar", "menu"),
        genericArt = setOf("food", "delivery", "restaurant", "recipe", "recipes", "pizza", "burger", "kitchen",
            "cooking", "chef", "coffee", "cafe", "fork", "knife", "cutlery", "meal", "drink"),
        brandArt = setOf("deliveroo", "glovo", "justeat", "ubereats", "doordash", "grubhub", "foodpanda",
            "thefork", "tripadvisor", "mcdonalds", "dominos", "starbucks", "kfc", "burgerking", "toogoodtogo",
            "yelp", "wolt", "zomato", "swiggy", "talabat", "cookidoo", "yummly", "paprika"),
        appPrefixes = setOf("food", "recipe", "pizza", "cook"),
        prefixExceptions = setOf("cookie", "cookies"),
    ),
    NEWS(
        "News and reading",
        appWords = setOf("news", "newspaper", "magazine", "reader", "reading", "read", "book", "books", "ebook",
            "ebooks", "comic", "comics", "manga", "rss", "feed", "feeds", "journal", "library", "novel", "novels",
            "audiobook", "audiobooks", "notizie", "giornale"),
        genericArt = setOf("news", "newspaper", "magazine", "reader", "book", "books", "ebook", "comic", "comics",
            "manga", "rss", "feed", "library", "read", "bookmark", "journal"),
        brandArt = setOf("kindle", "kobo", "moonreader", "librera", "readera", "koreader", "tachiyomi", "mihon",
            "kotatsu", "feedly", "inoreader", "flipboard", "pocket", "medium", "wattpad", "webtoon", "goodreads",
            "storytel", "audible", "ansa", "repubblica", "corriere", "guardian", "bbc", "cnn", "nytimes",
            "googlenews", "readwise", "instapaper", "readeck", "wallabag"),
        appPrefixes = setOf("news", "book", "comic", "manga", "read"),
        prefixExceptions = setOf("booking", "bookings", "ready", "readiness"),
    ),
    NOTES(
        "Notes and productivity",
        appWords = setOf("note", "notes", "notepad", "memo", "memos", "todo", "todos", "task", "tasks", "reminder",
            "reminders", "planner", "agenda", "journal", "diary", "docs", "document", "documents", "office", "pdf",
            "sheets", "spreadsheet", "word", "writer", "markdown", "wiki", "checklist", "list", "lists", "scanner"),
        genericArt = setOf("note", "notes", "notepad", "memo", "todo", "task", "tasks", "checklist", "check",
            "reminder", "planner", "notebook", "pencil", "pen", "document", "docs", "pdf", "office", "clipboard",
            "list", "writer", "word", "scanner"),
        brandArt = setOf("keep", "evernote", "onenote", "notion", "obsidian", "joplin", "standardnotes", "simplenote",
            "logseq", "todoist", "ticktick", "anydo", "wunderlist", "trello", "asana", "clickup", "microsofttodo",
            "word", "excel", "powerpoint", "googledocs", "sheets", "slides", "wps", "polaris", "onlyoffice",
            "adobeacrobat", "acrobat", "xodo", "camscanner", "markor", "quillpad", "notally", "squid", "goodnotes"),
        appPrefixes = setOf("note", "memo", "todo", "task", "doc", "pdf"),
        prefixExceptions = setOf("noteworthy", "docker", "doctor", "doctors", "taskbar"),
    ),
    FILES(
        "Files and storage",
        // Not "archive": ArchiveTune is a music app.
        appWords = setOf("file", "files", "explorer", "storage", "cloud", "drive", "backup", "sync", "unzip",
            "download", "downloader", "downloads", "torrent", "ftp", "nas"),
        genericArt = setOf("file", "files", "folder", "folders", "explorer", "storage", "cloud", "drive", "backup",
            "sync", "archive", "zip", "download", "downloads", "torrent", "sdcard", "harddisk", "database"),
        brandArt = setOf("dropbox", "onedrive", "googledrive", "mega", "pcloud", "nextcloud", "owncloud", "syncthing",
            "box", "icloud", "solidexplorer", "mixplorer", "materialfiles", "fxfileexplorer", "cxfileexplorer",
            "rar", "winrar", "zarchiver", "qbittorrent", "libretorrent", "utorrent", "bittorrent", "flud",
            "1dm", "adm", "seal", "ytdlnis", "hidisk", "filemanager", "documentsui"),
        appPrefixes = setOf("file", "cloud", "download", "torrent", "backup"),
    ),
    HEALTH(
        "Health and fitness",
        appWords = setOf("health", "fitness", "fit", "workout", "workouts", "gym", "run", "running", "runner",
            "steps", "step", "sleep", "yoga", "meditation", "meditate", "diet", "calorie", "calories", "training",
            "trainer", "bike", "cycling", "hiking", "swim", "medical", "doctor", "pharmacy", "salute", "period",
            "heart", "pedometer", "watch", "band"),
        genericArt = setOf("health", "fitness", "workout", "gym", "dumbbell", "running", "run", "steps", "sleep",
            "yoga", "meditation", "heart", "pulse", "medical", "medicine", "pill", "doctor", "hospital", "bike",
            "cycling", "hiking", "calories", "scale", "watch"),
        brandArt = setOf("strava", "fitbit", "garmin", "garminconnect", "samsunghealth", "googlefit", "fit",
            "myfitnesspal", "nike", "nikerunclub", "adidas", "runtastic", "headspace", "calm", "zepp", "mifit",
            "amazfit", "huaweihealth", "polar", "suunto", "komoot", "peloton", "fitbod", "flo", "clue", "sleepcycle",
            "healthconnect", "gadgetbridge", "healthy"),
        appPrefixes = setOf("health", "fitness", "workout", "yoga", "meditat", "sleep", "pedometer"),
    ),
    WEATHER(
        "Weather",
        appWords = setOf("weather", "meteo", "forecast", "radar", "climate", "wetter", "rain"),
        genericArt = setOf("weather", "meteo", "forecast", "radar", "cloud", "sun", "rain", "storm", "thermometer",
            "temperature", "umbrella", "climate"),
        brandArt = setOf("accuweather", "windy", "overdrop", "breezy", "geometricweather", "clima", "ilmeteo",
            "yahooweather", "weatherchannel", "rainviewer", "meteoblue", "zoomearth", "weawow", "totemweather"),
        appPrefixes = setOf("weather", "meteo", "forecast"),
    ),
    TRAVEL(
        "Travel",
        appWords = setOf("travel", "trip", "trips", "flight", "flights", "airline", "airlines", "airport", "hotel",
            "hotels", "booking", "bookings", "holiday", "holidays", "vacation", "tour", "tours", "voli", "viaggi",
            "rental", "car", "parking", "fuel"),
        genericArt = setOf("travel", "trip", "flight", "plane", "airplane", "aeroplane", "airport", "airline",
            "hotel", "suitcase", "luggage", "passport", "car", "parking", "fuel", "gas", "boarding"),
        brandArt = setOf("booking", "airbnb", "expedia", "skyscanner", "kayak", "tripadvisor", "trivago",
            "ryanair", "easyjet", "wizzair", "lufthansa", "ita", "alitalia", "emirates", "vueling", "hopper",
            "hostelworld", "agoda", "hotels", "kiwi", "flightradar", "flightradar24", "tripit", "getyourguide",
            "easypark", "telepass", "parclick", "flightaware"),
        appPrefixes = setOf("travel", "flight", "hotel", "airline", "trip"),
        prefixExceptions = setOf("tripod", "triple", "triplet"),
    ),
    TOOLS(
        "Tools and settings",
        appWords = setOf("tool", "tools", "toolkit", "utility", "utilities", "settings", "setup", "cleaner",
            "battery", "flashlight", "torch", "vpn", "proxy", "security", "antivirus", "password", "passwords",
            "authenticator", "auth", "2fa", "otp", "keyboard", "launcher", "widget", "widgets", "terminal", "shell",
            "remote", "wifi", "bluetooth", "network", "speedtest", "monitor", "benchmark", "root", "adb", "shizuku",
            "calculator", "converter", "clock", "alarm", "timer", "stopwatch", "recorder", "qr", "barcode"),
        genericArt = setOf("tool", "tools", "toolbox", "settings", "gear", "cog", "wrench", "hammer", "screwdriver",
            "utility", "cleaner", "battery", "flashlight", "torch", "vpn", "shield", "security", "lock", "key",
            "password", "authenticator", "keyboard", "terminal", "console", "wifi", "bluetooth", "network",
            "speedometer", "remote", "calculator", "clock", "alarm", "timer", "stopwatch", "recorder", "qr", "barcode"),
        brandArt = setOf("termux", "shizuku", "magisk", "bitwarden", "keepass", "keepassdx", "onepassword",
            "lastpass", "aegis", "authy", "protonvpn", "nordvpn", "mullvad", "wireguard", "openvpn", "cloudflare",
            "warp", "adguard", "blokada", "netguard", "rethink", "tasker", "macrodroid", "automate", "gboard",
            "swiftkey", "heliboard", "futo", "speedtest", "cpuz", "aida64", "devcheck", "accubattery", "sdmaid",
            "kdeconnect", "localsend", "anydesk", "teamviewer", "flashlight", "calculator", "deskclock"),
        appPrefixes = setOf("tool", "util", "setting", "clean", "battery", "flash", "secur", "password", "auth",
            "keyboard", "termin", "calcul", "alarm", "timer", "record"),
        prefixExceptions = setOf("authors", "author", "authority", "cleanse", "flashcard", "flashcards", "flashscore",
            "terminator", "recordings"),
    ),
    ;

    /** Whether one normalized app word implies this kind, including fused names such as "busitalia". */
    fun matchesAppWord(word: String): Boolean {
        if (word in appWords) return true
        if (word in prefixExceptions || prefixExceptions.any { word.startsWith(it) }) return false
        return appPrefixes.any { word.length > it.length + 1 && word.startsWith(it) }
    }

    companion object {
        /** Android's declared app categories ([android.content.pm.ApplicationInfo.category]). */
        fun fromAndroid(category: Int): AppCategory? = when (category) {
            0 -> GAMES
            1 -> MUSIC
            2 -> VIDEO
            3 -> PHOTO
            4 -> CHAT
            5 -> NEWS
            6 -> MAPS
            7 -> NOTES
            else -> null
        }
    }
}
