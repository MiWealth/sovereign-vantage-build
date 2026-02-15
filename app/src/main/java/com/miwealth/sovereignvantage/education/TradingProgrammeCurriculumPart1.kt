/**
 * SOVEREIGN VANTAGE V5.5.93 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - CURRICULUM DATA
 * 
 * Part 1: Modules 1-3 (Foundation, Technical Analysis, Fundamental Analysis)
 * Lessons 1-36
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */

package com.miwealth.sovereignvantage.education

/**
 * Complete 76-lesson institutional trading curriculum.
 * Split across two files for maintainability.
 */
object TradingProgrammeCurriculum {

    // ========================================================================
    // MODULE 1: FOUNDATION OF TRADING (Lessons 1-12)
    // ========================================================================
    
    private val module1Lessons = listOf(
        Lesson(
            id = 1,
            moduleId = 1,
            title = "Introduction to Financial Markets",
            description = "Understanding the global financial ecosystem and market participants",
            durationMinutes = 90,
            difficulty = Difficulty.BEGINNER,
            prerequisites = emptyList(),
            objectives = listOf(
                "Understand the structure of global financial markets",
                "Identify key market participants and their roles",
                "Recognise different asset classes and their characteristics"
            ),
            topics = listOf(
                "Global market structure",
                "Equity, fixed income, forex, commodities, crypto markets",
                "Market participants: retail, institutional, market makers",
                "Regulatory frameworks and compliance"
            ),
            practicalExercises = listOf(
                "Map the global market ecosystem",
                "Identify market hours across time zones",
                "Analyse market participant behaviour"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 70
        ),
        Lesson(
            id = 2,
            moduleId = 1,
            title = "Order Types and Execution",
            description = "Mastering different order types and optimal execution strategies",
            durationMinutes = 120,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(1),
            objectives = listOf(
                "Master all standard order types",
                "Understand order routing and execution",
                "Minimise slippage and execution costs"
            ),
            topics = listOf(
                "Market orders, limit orders, stop orders",
                "Advanced orders: OCO, bracket, trailing stops",
                "Order routing and best execution",
                "Dark pools and alternative trading systems"
            ),
            practicalExercises = listOf(
                "Execute trades using different order types",
                "Calculate execution costs and slippage",
                "Design optimal order execution strategies"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 3,
            moduleId = 1,
            title = "Trading Psychology: The Mental Game",
            description = "Developing the psychological edge required for consistent profitability",
            durationMinutes = 150,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(1),
            objectives = listOf(
                "Recognise cognitive biases affecting trading decisions",
                "Develop emotional discipline and control",
                "Build a resilient trading mindset"
            ),
            topics = listOf(
                "Cognitive biases: confirmation, anchoring, loss aversion",
                "Emotional management: fear, greed, FOMO",
                "Building trading discipline",
                "Journaling and self-reflection practices"
            ),
            practicalExercises = listOf(
                "Identify personal cognitive biases",
                "Create a trading journal template",
                "Develop pre-trade and post-trade routines"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 70
        ),
        Lesson(
            id = 4,
            moduleId = 1,
            title = "Market Microstructure",
            description = "Understanding how markets work at the micro level",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(1, 2),
            objectives = listOf(
                "Understand bid-ask spreads and market depth",
                "Analyse order flow and market impact",
                "Recognise market maker behaviour"
            ),
            topics = listOf(
                "Bid-ask spread dynamics",
                "Order book analysis",
                "Market maker strategies",
                "High-frequency trading impact"
            ),
            practicalExercises = listOf(
                "Analyse order book depth",
                "Calculate market impact costs",
                "Identify market maker activity"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 5,
            moduleId = 1,
            title = "Reading Price Action",
            description = "Interpreting raw price movements without indicators",
            durationMinutes = 180,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(1),
            objectives = listOf(
                "Read candlestick patterns effectively",
                "Identify support and resistance levels",
                "Recognise trend structure"
            ),
            topics = listOf(
                "Candlestick anatomy and patterns",
                "Support and resistance identification",
                "Trend analysis: higher highs, lower lows",
                "Price action context"
            ),
            practicalExercises = listOf(
                "Identify 20 key candlestick patterns",
                "Mark support/resistance on live charts",
                "Analyse trend structure across timeframes"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 6,
            moduleId = 1,
            title = "Time Frame Analysis",
            description = "Multi-timeframe analysis for comprehensive market view",
            durationMinutes = 120,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(5),
            objectives = listOf(
                "Understand timeframe hierarchy",
                "Align trades with higher timeframe trends",
                "Use multiple timeframes for entry precision"
            ),
            topics = listOf(
                "Timeframe hierarchy and relationships",
                "Top-down analysis methodology",
                "Timeframe confluence",
                "Optimal timeframes for different strategies"
            ),
            practicalExercises = listOf(
                "Perform top-down analysis on 5 assets",
                "Identify timeframe confluence zones",
                "Match timeframes to trading style"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 7,
            moduleId = 1,
            title = "Volume Analysis Fundamentals",
            description = "Using volume to confirm price movements and identify reversals",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(5),
            objectives = listOf(
                "Interpret volume in context of price",
                "Identify accumulation and distribution",
                "Use volume for trade confirmation"
            ),
            topics = listOf(
                "Volume-price relationships",
                "Accumulation and distribution patterns",
                "Volume climax and exhaustion",
                "On-balance volume and volume profile"
            ),
            practicalExercises = listOf(
                "Analyse volume patterns on 10 charts",
                "Identify accumulation/distribution zones",
                "Create volume-based trading rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 8,
            moduleId = 1,
            title = "Introduction to Risk Management",
            description = "Fundamental principles of protecting capital",
            durationMinutes = 150,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(1, 2),
            objectives = listOf(
                "Understand the importance of risk management",
                "Calculate position sizes correctly",
                "Set appropriate stop losses"
            ),
            topics = listOf(
                "Risk-reward ratios",
                "Position sizing basics",
                "Stop loss placement",
                "Maximum drawdown management"
            ),
            practicalExercises = listOf(
                "Calculate position sizes for 10 trades",
                "Design stop loss strategies",
                "Create a risk management checklist"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 80
        ),
        Lesson(
            id = 9,
            moduleId = 1,
            title = "Trading Plan Development",
            description = "Creating a comprehensive trading plan for consistent execution",
            durationMinutes = 180,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(1, 3, 8),
            objectives = listOf(
                "Develop a complete trading plan",
                "Define clear entry and exit rules",
                "Establish performance metrics"
            ),
            topics = listOf(
                "Components of a trading plan",
                "Rule-based trading systems",
                "Performance tracking metrics",
                "Plan review and iteration"
            ),
            practicalExercises = listOf(
                "Write a complete trading plan",
                "Define 5 specific trading rules",
                "Create a performance dashboard"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 10,
            moduleId = 1,
            title = "Paper Trading Mastery",
            description = "Maximising learning from simulated trading",
            durationMinutes = 120,
            difficulty = Difficulty.BEGINNER,
            prerequisites = listOf(9),
            objectives = listOf(
                "Use paper trading effectively for skill development",
                "Track and analyse simulated trades",
                "Transition from paper to live trading"
            ),
            topics = listOf(
                "Paper trading best practices",
                "Realistic simulation settings",
                "Performance analysis",
                "Graduation criteria to live trading"
            ),
            practicalExercises = listOf(
                "Execute 50 paper trades",
                "Analyse paper trading results",
                "Create transition plan to live trading"
            ),
            assessmentType = AssessmentType.SIMULATION,
            passingScore = 70
        ),
        Lesson(
            id = 11,
            moduleId = 1,
            title = "Market Regimes and Conditions",
            description = "Identifying and adapting to different market environments",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(5, 6),
            objectives = listOf(
                "Identify trending vs ranging markets",
                "Recognise volatility regimes",
                "Adapt strategies to market conditions"
            ),
            topics = listOf(
                "Trending markets: characteristics and strategies",
                "Ranging markets: characteristics and strategies",
                "Volatility regimes: low, normal, high",
                "Regime change identification"
            ),
            practicalExercises = listOf(
                "Classify 20 market periods by regime",
                "Match strategies to market conditions",
                "Develop regime detection rules"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 12,
            moduleId = 1,
            title = "Foundation Module Assessment",
            description = "Comprehensive assessment of foundation trading knowledge",
            durationMinutes = 180,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
            objectives = listOf(
                "Demonstrate mastery of foundation concepts",
                "Apply knowledge to real-world scenarios",
                "Earn Foundation Trader Certificate"
            ),
            topics = listOf(
                "Comprehensive review of all foundation topics",
                "Practical application scenarios",
                "Case study analysis"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Analyse 5 case studies",
                "Present trading plan to assessors"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 80
        )
    )

    val MODULE_1_FOUNDATION = Module(
        id = 1,
        name = "Foundation of Trading",
        description = "Essential concepts, market structure, and trading psychology fundamentals",
        lessons = module1Lessons,
        certification = "Foundation Trader Certificate",
        estimatedHours = 24
    )

    // ========================================================================
    // MODULE 2: TECHNICAL ANALYSIS MASTERY (Lessons 13-24)
    // ========================================================================
    
    private val module2Lessons = listOf(
        Lesson(
            id = 13,
            moduleId = 2,
            title = "Advanced Candlestick Patterns",
            description = "Deep dive into complex candlestick formations and their reliability",
            durationMinutes = 180,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(5, 12),
            objectives = listOf(
                "Master complex candlestick patterns",
                "Understand pattern reliability statistics",
                "Combine patterns with context"
            ),
            topics = listOf(
                "Three-candle patterns: morning/evening star, three soldiers/crows",
                "Complex patterns: abandoned baby, three inside up/down",
                "Pattern reliability and failure rates",
                "Context-dependent pattern interpretation"
            ),
            practicalExercises = listOf(
                "Identify 30 advanced patterns on charts",
                "Calculate pattern success rates",
                "Create pattern-based trading rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 14,
            moduleId = 2,
            title = "Chart Patterns: Continuation",
            description = "Identifying and trading continuation patterns",
            durationMinutes = 150,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(13),
            objectives = listOf(
                "Recognise all major continuation patterns",
                "Calculate pattern price targets",
                "Time entries and exits effectively"
            ),
            topics = listOf(
                "Flags, pennants, and wedges",
                "Rectangles and channels",
                "Cup and handle patterns",
                "Target calculation methods"
            ),
            practicalExercises = listOf(
                "Identify 20 continuation patterns",
                "Calculate price targets for each",
                "Backtest pattern performance"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 15,
            moduleId = 2,
            title = "Chart Patterns: Reversal",
            description = "Identifying and trading reversal patterns",
            durationMinutes = 150,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(13),
            objectives = listOf(
                "Recognise all major reversal patterns",
                "Distinguish true reversals from fakeouts",
                "Manage risk in reversal trades"
            ),
            topics = listOf(
                "Head and shoulders patterns",
                "Double and triple tops/bottoms",
                "Rounding patterns",
                "V-bottoms and spike reversals"
            ),
            practicalExercises = listOf(
                "Identify 20 reversal patterns",
                "Analyse pattern confirmation signals",
                "Develop reversal trading strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 16,
            moduleId = 2,
            title = "Moving Averages Deep Dive",
            description = "Advanced moving average strategies and systems",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(12),
            objectives = listOf(
                "Master different MA types and their uses",
                "Build MA-based trading systems",
                "Optimise MA parameters"
            ),
            topics = listOf(
                "SMA, EMA, WMA, DEMA, TEMA",
                "MA crossover systems",
                "MA as dynamic support/resistance",
                "Adaptive moving averages"
            ),
            practicalExercises = listOf(
                "Compare MA types on same data",
                "Build and test MA crossover system",
                "Optimise MA parameters"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 17,
            moduleId = 2,
            title = "Momentum Indicators",
            description = "RSI, Stochastic, and momentum-based trading",
            durationMinutes = 150,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(12),
            objectives = listOf(
                "Master RSI and Stochastic oscillators",
                "Identify divergences and their significance",
                "Build momentum-based strategies"
            ),
            topics = listOf(
                "RSI: calculation, interpretation, strategies",
                "Stochastic: fast, slow, full",
                "Momentum divergences",
                "Overbought/oversold trading"
            ),
            practicalExercises = listOf(
                "Identify 20 divergence setups",
                "Build RSI-based trading system",
                "Combine momentum indicators"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 18,
            moduleId = 2,
            title = "Volatility Indicators",
            description = "Bollinger Bands, ATR, and volatility-based trading",
            durationMinutes = 150,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(12),
            objectives = listOf(
                "Master Bollinger Bands strategies",
                "Use ATR for position sizing and stops",
                "Trade volatility expansion and contraction"
            ),
            topics = listOf(
                "Bollinger Bands: squeeze, breakout, mean reversion",
                "ATR: calculation and applications",
                "Keltner Channels",
                "Volatility-based position sizing"
            ),
            practicalExercises = listOf(
                "Identify Bollinger squeeze setups",
                "Calculate ATR-based stops",
                "Build volatility breakout system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 19,
            moduleId = 2,
            title = "MACD and Trend Indicators",
            description = "Advanced trend-following indicator strategies",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(16),
            objectives = listOf(
                "Master MACD interpretation and strategies",
                "Use ADX for trend strength measurement",
                "Combine trend indicators effectively"
            ),
            topics = listOf(
                "MACD: histogram, signal line, divergences",
                "ADX and DMI system",
                "Parabolic SAR",
                "Trend indicator combinations"
            ),
            practicalExercises = listOf(
                "Build MACD-based trading system",
                "Use ADX for trade filtering",
                "Create multi-indicator trend system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 20,
            moduleId = 2,
            title = "Fibonacci Analysis",
            description = "Fibonacci retracements, extensions, and time analysis",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(14, 15),
            objectives = listOf(
                "Master Fibonacci retracement levels",
                "Use Fibonacci extensions for targets",
                "Apply Fibonacci time analysis"
            ),
            topics = listOf(
                "Fibonacci sequence and golden ratio",
                "Retracement levels: 38.2%, 50%, 61.8%",
                "Extension levels: 127.2%, 161.8%, 261.8%",
                "Fibonacci time zones and clusters"
            ),
            practicalExercises = listOf(
                "Apply Fibonacci to 20 charts",
                "Identify confluence zones",
                "Build Fibonacci-based trading rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 21,
            moduleId = 2,
            title = "Elliott Wave Theory",
            description = "Understanding market cycles through Elliott Wave analysis",
            durationMinutes = 240,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(20),
            objectives = listOf(
                "Understand Elliott Wave principles",
                "Identify impulse and corrective waves",
                "Apply wave analysis to trading"
            ),
            topics = listOf(
                "Wave structure: 5-3 pattern",
                "Impulse waves and their rules",
                "Corrective patterns: zigzag, flat, triangle",
                "Wave counting methodology"
            ),
            practicalExercises = listOf(
                "Count waves on 10 charts",
                "Identify current wave position",
                "Develop wave-based trading plan"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 22,
            moduleId = 2,
            title = "Volume Profile Analysis",
            description = "Advanced volume analysis using volume profile",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(7, 12),
            objectives = listOf(
                "Understand volume profile concepts",
                "Identify value areas and POC",
                "Trade using volume profile levels"
            ),
            topics = listOf(
                "Volume profile construction",
                "Value Area High/Low (VAH/VAL)",
                "Point of Control (POC)",
                "Volume nodes and gaps"
            ),
            practicalExercises = listOf(
                "Analyse volume profile on 10 charts",
                "Identify key volume levels",
                "Build volume profile trading strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 23,
            moduleId = 2,
            title = "Market Profile and Auction Theory",
            description = "Understanding market structure through auction theory",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(22),
            objectives = listOf(
                "Understand auction market theory",
                "Read market profile charts",
                "Identify market balance and imbalance"
            ),
            topics = listOf(
                "Auction market theory principles",
                "TPO (Time Price Opportunity) charts",
                "Initial balance and range extension",
                "Market structure analysis"
            ),
            practicalExercises = listOf(
                "Analyse market profile for 5 sessions",
                "Identify balance/imbalance conditions",
                "Develop auction-based trading rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 24,
            moduleId = 2,
            title = "Technical Analysis Module Assessment",
            description = "Comprehensive assessment of technical analysis mastery",
            durationMinutes = 240,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23),
            objectives = listOf(
                "Demonstrate mastery of technical analysis",
                "Apply multiple techniques to real charts",
                "Earn Technical Analyst Certificate"
            ),
            topics = listOf(
                "Comprehensive technical analysis review",
                "Multi-technique chart analysis",
                "Live market analysis"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Analyse 10 charts using multiple techniques",
                "Present technical analysis to assessors"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 80
        )
    )

    val MODULE_2_TECHNICAL = Module(
        id = 2,
        name = "Technical Analysis Mastery",
        description = "Advanced chart analysis, indicators, and pattern recognition",
        lessons = module2Lessons,
        certification = "Technical Analyst Certificate",
        estimatedHours = 30
    )

    // ========================================================================
    // MODULE 3: FUNDAMENTAL ANALYSIS (Lessons 25-36)
    // ========================================================================
    
    private val module3Lessons = listOf(
        Lesson(
            id = 25,
            moduleId = 3,
            title = "Macroeconomic Analysis",
            description = "Understanding economic indicators and their market impact",
            durationMinutes = 180,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(12),
            objectives = listOf(
                "Interpret key economic indicators",
                "Understand central bank policies",
                "Anticipate market reactions to economic data"
            ),
            topics = listOf(
                "GDP, inflation, employment data",
                "Central bank policies and interest rates",
                "Economic cycles and their phases",
                "Intermarket relationships"
            ),
            practicalExercises = listOf(
                "Analyse economic calendar for one month",
                "Track market reactions to data releases",
                "Build economic indicator dashboard"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 26,
            moduleId = 3,
            title = "Financial Statement Analysis",
            description = "Reading and interpreting company financial statements",
            durationMinutes = 180,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(25),
            objectives = listOf(
                "Read income statements, balance sheets, cash flow",
                "Calculate key financial ratios",
                "Identify red flags in financial statements"
            ),
            topics = listOf(
                "Income statement analysis",
                "Balance sheet analysis",
                "Cash flow statement analysis",
                "Financial ratio analysis"
            ),
            practicalExercises = listOf(
                "Analyse 5 company financial statements",
                "Calculate 20 key ratios",
                "Identify financial red flags"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 27,
            moduleId = 3,
            title = "Equity Valuation Methods",
            description = "Determining fair value of stocks using multiple methods",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(26),
            objectives = listOf(
                "Apply DCF valuation methodology",
                "Use comparable company analysis",
                "Understand precedent transaction analysis"
            ),
            topics = listOf(
                "Discounted Cash Flow (DCF) analysis",
                "Comparable company analysis",
                "Precedent transaction analysis",
                "Sum-of-the-parts valuation"
            ),
            practicalExercises = listOf(
                "Build DCF model for 3 companies",
                "Perform comp analysis",
                "Determine fair value ranges"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 28,
            moduleId = 3,
            title = "Sector and Industry Analysis",
            description = "Understanding sector dynamics and industry positioning",
            durationMinutes = 150,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(25),
            objectives = listOf(
                "Analyse sector rotation patterns",
                "Understand industry competitive dynamics",
                "Identify sector leaders and laggards"
            ),
            topics = listOf(
                "Sector rotation theory",
                "Porter's Five Forces",
                "Industry life cycles",
                "Competitive advantage analysis"
            ),
            practicalExercises = listOf(
                "Analyse 5 different sectors",
                "Apply Five Forces to 3 industries",
                "Identify sector rotation opportunities"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 29,
            moduleId = 3,
            title = "Cryptocurrency Fundamental Analysis",
            description = "Evaluating crypto projects using fundamental metrics",
            durationMinutes = 180,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(25),
            objectives = listOf(
                "Evaluate crypto project fundamentals",
                "Analyse on-chain metrics",
                "Assess tokenomics and governance"
            ),
            topics = listOf(
                "Whitepaper analysis",
                "Team and development activity",
                "On-chain metrics: NVT, MVRV, SOPR",
                "Tokenomics evaluation"
            ),
            practicalExercises = listOf(
                "Analyse 5 crypto project fundamentals",
                "Calculate on-chain metrics",
                "Evaluate tokenomics models"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 30,
            moduleId = 3,
            title = "DeFi Protocol Analysis",
            description = "Evaluating decentralised finance protocols",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(29),
            objectives = listOf(
                "Understand DeFi protocol mechanics",
                "Evaluate protocol risk and reward",
                "Analyse TVL and yield sustainability"
            ),
            topics = listOf(
                "DEX mechanics and AMM models",
                "Lending protocol analysis",
                "Yield farming evaluation",
                "Smart contract risk assessment"
            ),
            practicalExercises = listOf(
                "Analyse 5 DeFi protocols",
                "Calculate yield sustainability",
                "Assess smart contract risks"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 31,
            moduleId = 3,
            title = "News and Sentiment Analysis",
            description = "Interpreting news flow and market sentiment",
            durationMinutes = 120,
            difficulty = Difficulty.INTERMEDIATE,
            prerequisites = listOf(25),
            objectives = listOf(
                "Analyse news impact on markets",
                "Use sentiment indicators effectively",
                "Distinguish noise from signal"
            ),
            topics = listOf(
                "News analysis methodology",
                "Sentiment indicators: Fear & Greed, Put/Call",
                "Social media sentiment analysis",
                "Contrarian indicators"
            ),
            practicalExercises = listOf(
                "Analyse news impact on 10 events",
                "Track sentiment indicators",
                "Build sentiment-based trading rules"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 32,
            moduleId = 3,
            title = "Earnings Analysis and Trading",
            description = "Trading around earnings announcements",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(26, 27),
            objectives = listOf(
                "Analyse earnings reports effectively",
                "Understand earnings expectations",
                "Develop earnings trading strategies"
            ),
            topics = listOf(
                "Earnings expectations and surprises",
                "Pre-earnings positioning",
                "Post-earnings drift",
                "Options strategies for earnings"
            ),
            practicalExercises = listOf(
                "Analyse 10 earnings reports",
                "Track earnings surprise patterns",
                "Develop earnings trading strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 33,
            moduleId = 3,
            title = "Global Macro Trading",
            description = "Trading based on macroeconomic themes",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(25, 28),
            objectives = listOf(
                "Identify global macro themes",
                "Build macro-based trade ideas",
                "Manage macro portfolio risk"
            ),
            topics = listOf(
                "Global macro strategy overview",
                "Theme identification and development",
                "Cross-asset macro trading",
                "Macro risk management"
            ),
            practicalExercises = listOf(
                "Identify 5 current macro themes",
                "Build macro trade ideas",
                "Create macro portfolio"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 34,
            moduleId = 3,
            title = "Quantitative Fundamental Analysis",
            description = "Using quantitative methods for fundamental analysis",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(26, 27),
            objectives = listOf(
                "Apply quantitative screens to fundamentals",
                "Build factor-based models",
                "Backtest fundamental strategies"
            ),
            topics = listOf(
                "Quantitative screening methods",
                "Factor models: value, quality, momentum",
                "Fundamental backtesting",
                "Combining quant and discretionary"
            ),
            practicalExercises = listOf(
                "Build quantitative screens",
                "Backtest fundamental factors",
                "Create hybrid strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 35,
            moduleId = 3,
            title = "Alternative Data Analysis",
            description = "Using alternative data sources for trading edge",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(31, 34),
            objectives = listOf(
                "Identify valuable alternative data sources",
                "Analyse satellite, web, and social data",
                "Integrate alternative data into analysis"
            ),
            topics = listOf(
                "Alternative data landscape",
                "Satellite imagery analysis",
                "Web scraping and traffic data",
                "Social media and sentiment data"
            ),
            practicalExercises = listOf(
                "Identify 5 alternative data sources",
                "Analyse alternative data signals",
                "Build alternative data strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 36,
            moduleId = 3,
            title = "Fundamental Analysis Module Assessment",
            description = "Comprehensive assessment of fundamental analysis mastery",
            durationMinutes = 240,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35),
            objectives = listOf(
                "Demonstrate mastery of fundamental analysis",
                "Apply multiple valuation methods",
                "Earn Fundamental Analyst Certificate"
            ),
            topics = listOf(
                "Comprehensive fundamental analysis review",
                "Multi-method valuation",
                "Live company analysis"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Perform full company analysis",
                "Present investment thesis to assessors"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 80
        )
    )

    val MODULE_3_FUNDAMENTAL = Module(
        id = 3,
        name = "Fundamental Analysis",
        description = "Economic analysis, valuation methods, and fundamental trading",
        lessons = module3Lessons,
        certification = "Fundamental Analyst Certificate",
        estimatedHours = 28
    )
}
