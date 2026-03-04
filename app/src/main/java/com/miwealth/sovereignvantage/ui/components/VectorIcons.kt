package com.miwealth.sovereignvantage.ui.theme

/**
 * AEGIS LUXURY ICON SYSTEM (Vector Paths)
 * 
 * STYLE GUIDE:
 * - Stroke Width: 1.5dp (Thin, Elegant)
 * - Color: Gold (#D4AF37) or Platinum (#E5E4E2)
 * - Shape: Geometric, Sharp Angles (No rounded "cartoon" edges)
 * 
 * Copyright: © 2025 MiWealth Pty Ltd. All Rights Reserved.
 */
object VectorIcons {

    // THE SHIELD (Logo)
    const val ICON_SHIELD = """
        M12,1 L3,5 v6 c0,5.55 3.84,10.74 9,12 c5.16,-1.26 9,-6.45 9,-12 V5 L12,1 Z
        M12,12 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0
    """

    // THE BRAIN (AI / XAI)
    const val ICON_BRAIN = """
        M12,2 C6.48,2 2,6.48 2,12 C2,17.52 6.48,22 12,22 C17.52,22 22,17.52 22,12 C22,6.48 17.52,2 12,2 Z
        M12,6 v3 M9,9 l2,2 M15,9 l-2,2 M12,12 v6
    """

    // THE RADAR (Trading)
    const val ICON_RADAR = """
        M12,2 A10,10 0 1,1 2,12 A10,10 0 0,1 12,2 Z
        M12,12 L18,6
        M12,8 A4,4 0 1,1 8,12 A4,4 0 0,1 12,8 Z
    """

    // THE VAULT (Wallet)
    const val ICON_VAULT = """
        M4,4 H20 V20 H4 Z
        M12,12 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0
        M12,8 v-2 M12,18 v-2 M8,12 h-2 M16,12 h-2
    """

    // THE CROWN (Elite/Apex Tier)
    const val ICON_CROWN = """
        M5,18 h14 v2 H5 Z
        M5,16 l2,-8 l4,4 l4,-4 l2,8 H5 Z
    """
}
