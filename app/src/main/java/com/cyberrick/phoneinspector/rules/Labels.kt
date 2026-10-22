package com.cyberrick.phoneinspector.rules

/** The words on a threat card. Fixed set so the UI can style them and a test can check every rule uses one. */
object Labels {
    const val KNOWN_MALWARE = "Known malware"
    const val STEALS_DATA = "Steals data"
    const val HIDDEN = "Hidden"
    const val READS_SCREEN = "Reads your screen"
    const val CAN_WIPE = "Can wipe this phone"
    const val READS_MESSAGES = "Reads your messages"
    const val ADWARE_HEAVY = "Adware heavy"
    const val REVIEW = "Review"
    const val NOTE = "Note"

    val ALL = setOf(KNOWN_MALWARE, STEALS_DATA, HIDDEN, READS_SCREEN, CAN_WIPE, READS_MESSAGES, ADWARE_HEAVY, REVIEW, NOTE)
}
