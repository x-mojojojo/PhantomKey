package com.phantomkey.app.crypto

/**
 * Password type templates from the Master Password algorithm (v3).
 * Each type maps to one or more character-class templates.
 */
enum class PasswordType(
    val displayName: String,
    val templates: Array<String>,
) {
    MAXIMUM(
        displayName = "Maximum",
        templates = arrayOf(
            "anoxxxxxxxxxxxxxxxxx",
            "axxxxxxxxxxxxxxxxxno",
        ),
    ),
    LONG(
        displayName = "Long",
        templates = arrayOf(
            "CvcvnoCvcvCvcv",
            "CvcvCvcvnoCvcv",
            "CvcvCvcvCvcvno",
            "CvccnoCvcvCvcv",
            "CvccCvcvnoCvcv",
            "CvccCvcvCvcvno",
            "CvcvnoCvccCvcv",
            "CvcvCvccnoCvcv",
            "CvcvCvccCvcvno",
            "CvcvnoCvcvCvcc",
            "CvcvCvcvnoCvcc",
            "CvcvCvcvCvccno",
            "CvccnoCvccCvcv",
            "CvccCvccnoCvcv",
            "CvccCvccCvcvno",
            "CvcvnoCvccCvcc",
            "CvcvCvccnoCvcc",
            "CvcvCvccCvccno",
            "CvccnoCvcvCvcc",
            "CvccCvcvnoCvcc",
            "CvccCvcvCvccno",
        ),
    ),
    MEDIUM(
        displayName = "Medium",
        templates = arrayOf(
            "CvcnoCvc",
            "CvcCvcno",
        ),
    ),
    BASIC(
        displayName = "Basic",
        templates = arrayOf(
            "aaanaaan",
            "aannaaan",
            "aaannaaa",
        ),
    ),
    SHORT(
        displayName = "Short",
        templates = arrayOf("Cvcn"),
    ),
    PIN(
        displayName = "PIN",
        templates = arrayOf("nnnn"),
    ),
    NAME(
        displayName = "Name",
        templates = arrayOf("cvccvcvcv"),
    ),
    PHRASE(
        displayName = "Phrase",
        templates = arrayOf(
            "cvcc cvc cvccvcv cvc",
            "cvc cvccvcvcv cvcv",
            "cv cvccv cvc cvcvccv",
        ),
    );

    companion object {
        fun fromName(name: String): PasswordType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: MAXIMUM
    }
}
