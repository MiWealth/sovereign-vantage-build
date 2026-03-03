package com.miwealth.sovereignvantage.education

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - QUIZ QUESTION BANK
 * 
 * 228 questions (3 per lesson) covering foundation to mastery.
 * Explanations merged at runtime from QuizExplanations.kt (incremental population).
 * 
 * Each question has 4 options with 1 correct answer (0-indexed).
 * Questions are shuffled at runtime for re-test fairness.
 * 
 * Copyright © 2025-2026 MiWealth Pty Ltd. All rights reserved.
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */


/**
 * Singleton question bank providing quiz questions for all 76 lessons.
 * Questions are stored in memory (small footprint, ~50KB).
 */


object QuizQuestionBank {

    /**
     * Get questions for a specific lesson.
     * @param lessonId Lesson ID (1-76)
     * @param shuffle If true, randomise question and option order
     * @return List of QuizQuestion for the lesson
     */
    fun getQuestionsForLesson(lessonId: Int, shuffle: Boolean = true): List<QuizQuestion> {
        val questions = allQuestions.filter { it.lessonId == lessonId }.map { q ->
            // Merge explanation from QuizExplanations if available and not already set
            if (q.explanation.isBlank()) {
                val explanation = QuizExplanations.getExplanation(q.id)
                if (explanation != null) q.copy(explanation = explanation) else q
            } else q
        }
        return if (shuffle) questions.shuffled() else questions
    }

    /**
     * Get a subset of questions for quick quiz.
     */
    fun getQuickQuiz(lessonId: Int, count: Int = 3): List<QuizQuestion> {
        return getQuestionsForLesson(lessonId, shuffle = true).take(count)
    }

    /**
     * Get all questions for a module assessment.
     * Pulls questions from all lessons in that module.
     */
    fun getModuleAssessment(moduleId: Int, questionsPerLesson: Int = 2): List<QuizQuestion> {
        val lessonRange = when (moduleId) {
            1 -> 1..12
            2 -> 13..24
            3 -> 25..36
            4 -> 37..48
            5 -> 49..60
            6 -> 61..72
            7 -> 73..76
            else -> IntRange.EMPTY
        }
        return lessonRange.flatMap { lessonId ->
            getQuestionsForLesson(lessonId, shuffle = true).take(questionsPerLesson)
        }.shuffled()
    }

    /**
     * Total question count.
     */
    val totalQuestions: Int get() = allQuestions.size

    /**
     * Number of questions with explanations populated.
     */
    val explainedQuestions: Int get() = QuizExplanations.populatedCount

    // ========================================================================
    // MODULE 1: FOUNDATION (Lessons 1-12)
    // ========================================================================

    private val allQuestions: List<QuizQuestion> = listOf(

        // --- Lesson 1: Introduction to Financial Markets ---
        QuizQuestion(1, 1, "Which participant typically provides continuous two-sided quotes to ensure market liquidity?",
            listOf("Retail trader", "Market maker", "Central bank", "Hedge fund"), 1),
        QuizQuestion(2, 1, "In a typical market ecosystem, what is the primary role of a clearinghouse?",
            listOf("Setting interest rates", "Guaranteeing settlement of trades between counterparties", "Issuing new securities", "Providing investment advice"), 1),
        QuizQuestion(3, 1, "Which asset class is characterised by fixed periodic payments and return of principal at maturity?",
            listOf("Equities", "Commodities", "Fixed income", "Foreign exchange"), 2),

        // --- Lesson 2: Order Types and Execution ---
        QuizQuestion(4, 2, "A stop-limit order differs from a stop-market order in that it:",
            listOf("Guarantees execution at any price", "Converts to a limit order when triggered rather than a market order", "Can only be used for selling", "Executes before the stop price is reached"), 1),
        QuizQuestion(5, 2, "What is the primary purpose of an OCO (One-Cancels-Other) order?",
            listOf("To buy two assets simultaneously", "To set both a take-profit and stop-loss where triggering one cancels the other", "To cancel all open orders", "To place orders on two exchanges"), 1),
        QuizQuestion(6, 2, "Which execution venue characteristic is most associated with reduced market impact for large orders?",
            listOf("High transparency", "Dark pool routing", "Narrow spreads", "Low latency"), 1),

        // --- Lesson 3: Trading Psychology ---
        QuizQuestion(7, 3, "Loss aversion in trading typically manifests as:",
            listOf("Taking profits too early and letting losses run", "Taking equal-sized positions on every trade", "Ignoring market fundamentals", "Over-diversifying a portfolio"), 0),
        QuizQuestion(8, 3, "Which cognitive bias causes traders to seek information that confirms their existing market view?",
            listOf("Anchoring bias", "Confirmation bias", "Survivorship bias", "Recency bias"), 1),
        QuizQuestion(9, 3, "The disposition effect describes a trader's tendency to:",
            listOf("Trade too frequently", "Sell winners too early and hold losers too long", "Follow the crowd", "Overweight recent events"), 1),

        // --- Lesson 4: Market Microstructure ---
        QuizQuestion(10, 4, "The bid-ask spread primarily compensates market makers for:",
            listOf("Transaction taxes", "Inventory risk and adverse selection", "Technology costs only", "Regulatory compliance"), 1),
        QuizQuestion(11, 4, "What does 'price improvement' mean in order execution?",
            listOf("The price went up after your purchase", "Your order was filled at a better price than the quoted spread", "The exchange reduced fees", "A limit order was converted to market"), 1),
        QuizQuestion(12, 4, "In an order-driven market, price discovery occurs through:",
            listOf("A single designated market maker", "The interaction of submitted buy and sell orders in the book", "Central bank intervention", "After-hours negotiation"), 1),

        // --- Lesson 5: Reading Price Action ---
        QuizQuestion(13, 5, "A 'doji' candlestick pattern indicates:",
            listOf("Strong bullish momentum", "Strong bearish momentum", "Market indecision — open and close are nearly equal", "A gap in trading"), 2),
        QuizQuestion(14, 5, "An 'engulfing' pattern is significant because:",
            listOf("It always predicts reversals", "The second candle's body completely engulfs the first, suggesting momentum shift", "It only occurs at market tops", "It requires volume confirmation to be valid"), 1),
        QuizQuestion(15, 5, "Pin bars (hammer/shooting star) are most reliable when they occur:",
            listOf("In the middle of a range", "At key support or resistance levels", "On low-volume days", "Only on daily timeframes"), 1),

        // --- Lesson 6: Time Frame Analysis ---
        QuizQuestion(16, 6, "In multi-timeframe analysis, the higher timeframe is primarily used to:",
            listOf("Time exact entries", "Determine the prevailing trend and key levels", "Calculate position size", "Set stop-loss distance"), 1),
        QuizQuestion(17, 6, "When the daily chart shows an uptrend but the 1-hour chart shows a pullback, this typically represents:",
            listOf("A trend reversal", "An opportunity to enter with the higher-timeframe trend", "A signal to go short", "Conflicting signals requiring no action"), 1),
        QuizQuestion(18, 6, "Which combination of timeframes follows the 'factor of 4-6' rule for swing trading?",
            listOf("1m, 5m, 15m", "Daily, 4-hour, 1-hour", "Weekly, monthly, yearly", "5m, 15m, 30m"), 1),

        // --- Lesson 7: Volume Analysis Fundamentals ---
        QuizQuestion(19, 7, "Healthy trend continuation is typically characterised by:",
            listOf("Decreasing volume on trend moves", "Increasing volume on trend moves and decreasing volume on pullbacks", "Equal volume on all bars", "Volume spikes only at reversals"), 1),
        QuizQuestion(20, 7, "A 'volume climax' at the end of a prolonged trend often signals:",
            listOf("Trend continuation", "Potential exhaustion and reversal", "The start of a new trend", "No meaningful information"), 1),
        QuizQuestion(21, 7, "On-Balance Volume (OBV) is calculated by:",
            listOf("Averaging volume over 20 periods", "Adding volume on up-days and subtracting on down-days cumulatively", "Dividing price change by volume", "Multiplying close price by volume"), 1),

        // --- Lesson 8: Introduction to Risk Management ---
        QuizQuestion(22, 8, "A risk-reward ratio of 1:3 means:",
            listOf("You risk AU$3 to make AU$1", "You risk AU$1 to potentially make AU$3", "Your win rate must be 30%", "Your position size is 3% of capital"), 1),
        QuizQuestion(23, 8, "The '2% rule' in risk management states that:",
            listOf("You should allocate 2% of capital to each asset", "No single trade should risk more than 2% of total trading capital", "You need 2% daily returns", "Fees should not exceed 2%"), 1),
        QuizQuestion(24, 8, "Which metric best measures risk-adjusted return performance?",
            listOf("Total return", "Win rate", "Sharpe ratio", "Maximum profit"), 2),

        // --- Lesson 9: Trading Plan Development ---
        QuizQuestion(25, 9, "A comprehensive trading plan should include all EXCEPT:",
            listOf("Entry and exit criteria", "Position sizing rules", "Guaranteed profit targets", "Risk management protocols"), 2),
        QuizQuestion(26, 9, "The primary purpose of a trading journal is to:",
            listOf("Record profits for tax purposes", "Identify patterns in your decision-making and improve over time", "Show results to investors", "Track market news"), 1),
        QuizQuestion(27, 9, "Pre-market analysis in a trading plan should focus on:",
            listOf("Only yesterday's price action", "Key levels, overnight developments, economic calendar, and market context", "Social media sentiment only", "Checking profitability of other traders"), 1),

        // --- Lesson 10: Paper Trading Mastery ---
        QuizQuestion(28, 10, "The biggest risk of extended paper trading is:",
            listOf("Running out of simulated capital", "Developing habits without real emotional pressure affecting decisions", "Missing market opportunities", "Learning incorrect strategies"), 1),
        QuizQuestion(29, 10, "When transitioning from paper to live trading, best practice is to:",
            listOf("Immediately trade full position sizes", "Start with significantly reduced position sizes and scale up gradually", "Only trade during high volatility", "Switch to different strategies"), 1),
        QuizQuestion(30, 10, "Paper trading is most valuable when you:",
            listOf("Trade randomly to test market randomness", "Treat it exactly as you would real capital with full journaling and rules", "Skip risk management since there's no real risk", "Focus only on winning trades"), 1),

        // --- Lesson 11: Market Regimes and Conditions ---
        QuizQuestion(31, 11, "The ADX (Average Directional Index) above 25 typically indicates:",
            listOf("A ranging market", "A trending market with directional strength", "Market is about to reverse", "Low volume conditions"), 1),
        QuizQuestion(32, 11, "During a 'CRASH_MODE' regime, the recommended risk multiplier is:",
            listOf("2.0x (double risk for opportunity)", "1.0x (normal risk)", "0.5x (half risk)", "0.0x (no trading — capital preservation)"), 3),
        QuizQuestion(33, 11, "Mean reversion strategies tend to perform best in which regime?",
            listOf("Strong trending markets", "Sideways/ranging markets", "Crash mode", "Breakout pending"), 1),

        // --- Lesson 12: Foundation Module Assessment ---
        QuizQuestion(34, 12, "Which order type would you use to enter a long position only if price reaches a specific level above the current price?",
            listOf("Market order", "Buy limit order", "Buy stop order", "Sell stop order"), 2),
        QuizQuestion(35, 12, "A trader with a 40% win rate can be profitable if:",
            listOf("They increase position size on every trade", "Their average win is sufficiently larger than their average loss", "They only trade in bull markets", "They ignore risk management"), 1),
        QuizQuestion(36, 12, "The primary advantage of self-sovereign trading platforms over custodial services is:",
            listOf("Higher returns", "Lower fees", "Users maintain full control of their keys and funds at all times", "Better customer support"), 2),

        // ========================================================================
        // MODULE 2: TECHNICAL ANALYSIS (Lessons 13-24)
        // ========================================================================

        // --- Lesson 13: Advanced Candlestick Patterns ---
        QuizQuestion(37, 13, "A 'morning star' three-candle pattern consists of:",
            listOf("Three bullish candles", "A bearish candle, small-body candle, then bullish candle", "Three doji candles", "A gap up, reversal, and gap down"), 1),
        QuizQuestion(38, 13, "The reliability of candlestick patterns generally increases when:",
            listOf("They form on 1-minute charts", "They occur at significant support/resistance with confirming volume", "They appear in isolation", "The candles are very small"), 1),
        QuizQuestion(39, 13, "'Three black crows' pattern signals:",
            listOf("Bullish continuation", "Strong bearish momentum — three consecutive declining candles with lower closes", "Market indecision", "A bullish reversal"), 1),

        // --- Lesson 14: Chart Patterns: Continuation ---
        QuizQuestion(40, 14, "A bull flag pattern is characterised by:",
            listOf("A sharp decline followed by consolidation", "A sharp advance (flagpole) followed by a downward-sloping channel", "A horizontal rectangle after a decline", "An ascending triangle"), 1),
        QuizQuestion(41, 14, "The typical price target for a flag pattern is calculated by:",
            listOf("Doubling the current price", "Adding the flagpole length to the breakout point", "Measuring the flag height", "Using Fibonacci extensions only"), 1),
        QuizQuestion(42, 14, "Symmetrical triangles indicate:",
            listOf("Always bullish continuation", "Always bearish reversal", "Contraction of volatility — breakout direction determines trend", "Immediate crash imminent"), 2),

        // --- Lesson 15: Chart Patterns: Reversal ---
        QuizQuestion(43, 15, "In a head and shoulders top, the 'neckline' is drawn:",
            listOf("Across the top of the head", "Connecting the lows between the left shoulder, head, and right shoulder", "Horizontally from the left shoulder", "At the highest point of the pattern"), 1),
        QuizQuestion(44, 15, "A double bottom is confirmed when:",
            listOf("The second bottom is lower than the first", "Price breaks above the resistance level between the two bottoms", "Volume decreases on the second bottom", "The pattern takes exactly two weeks"), 1),
        QuizQuestion(45, 15, "Which reversal pattern typically takes the longest to form?",
            listOf("V-bottom", "Double top", "Head and shoulders", "Rounding bottom (saucer)"), 3),

        // --- Lesson 16: Moving Averages Deep Dive ---
        QuizQuestion(46, 16, "An EMA differs from an SMA in that it:",
            listOf("Uses volume weighting", "Places more weight on recent prices, reacting faster to changes", "Ignores the most recent data point", "Can only be calculated on daily timeframes"), 1),
        QuizQuestion(47, 16, "The 'golden cross' occurs when:",
            listOf("Price crosses above the 200 EMA", "The 50-period MA crosses above the 200-period MA", "Two EMAs touch but don't cross", "The 200 MA turns flat"), 1),
        QuizQuestion(48, 16, "DEMA (Double Exponential Moving Average) is designed to:",
            listOf("Use two separate EMAs", "Reduce the lag inherent in standard moving averages", "Double the lookback period", "Only work on weekly charts"), 1),

        // --- Lesson 17: Momentum Indicators ---
        QuizQuestion(49, 17, "RSI values above 70 traditionally indicate:",
            listOf("Strong buying opportunity", "Overbought conditions — potential for pullback or reversal", "Market equilibrium", "Low volatility"), 1),
        QuizQuestion(50, 17, "Bullish divergence on RSI occurs when:",
            listOf("Price and RSI both make higher highs", "Price makes lower lows while RSI makes higher lows", "RSI stays above 50", "Price and RSI move in the same direction"), 1),
        QuizQuestion(51, 17, "The Stochastic oscillator measures:",
            listOf("Volume relative to average", "Current close relative to the high-low range over a lookback period", "Trend direction", "Volatility expansion"), 1),

        // --- Lesson 18: Volatility Indicators ---
        QuizQuestion(52, 18, "A Bollinger Band 'squeeze' (narrowing bands) suggests:",
            listOf("Trend is about to end", "Volatility contraction that often precedes a significant move", "Price will remain range-bound", "Trading should be avoided"), 1),
        QuizQuestion(53, 18, "ATR (Average True Range) is primarily used to:",
            listOf("Determine trend direction", "Measure market volatility for stop-loss placement and position sizing", "Identify overbought conditions", "Calculate moving averages"), 1),
        QuizQuestion(54, 18, "Keltner Channels differ from Bollinger Bands in that they use:",
            listOf("Volume instead of price", "ATR-based bands rather than standard deviation", "Only closing prices", "A fixed width"), 1),

        // --- Lesson 19: MACD and Trend Indicators ---
        QuizQuestion(55, 19, "The MACD histogram represents:",
            listOf("Absolute price momentum", "The difference between the MACD line and the signal line", "Volume-weighted momentum", "The trend direction only"), 1),
        QuizQuestion(56, 19, "MACD divergence is most significant when it occurs:",
            listOf("In a range-bound market", "After an extended trend, at key support/resistance", "On 1-minute charts", "When volume is low"), 1),
        QuizQuestion(57, 19, "ADX (Average Directional Index) measures:",
            listOf("Price direction only", "Trend strength regardless of direction", "Volume momentum", "Volatility cycles"), 1),

        // --- Lesson 20: Fibonacci Analysis ---
        QuizQuestion(58, 20, "The key Fibonacci retracement levels used in trading are:",
            listOf("10%, 25%, 50%, 75%", "23.6%, 38.2%, 50%, 61.8%, 78.6%", "20%, 40%, 60%, 80%", "33%, 50%, 66%"), 1),
        QuizQuestion(59, 20, "Fibonacci extensions are primarily used to:",
            listOf("Identify entry points", "Project potential profit targets beyond the prior swing", "Calculate risk-reward ratios", "Set stop-loss levels only"), 1),
        QuizQuestion(60, 20, "The 61.8% retracement level is significant because:",
            listOf("It represents the average pullback", "It approximates the golden ratio and frequently acts as support/resistance", "It is the exact midpoint", "It only works on daily charts"), 1),

        // --- Lesson 21: Elliott Wave Theory ---
        QuizQuestion(61, 21, "In Elliott Wave theory, a complete market cycle consists of:",
            listOf("3 waves up and 3 waves down", "5 impulse waves and 3 corrective waves", "8 equal waves", "Random wave patterns"), 1),
        QuizQuestion(62, 21, "Wave 3 in an Elliott impulse sequence is typically:",
            listOf("The shortest wave", "The longest and strongest wave, never the shortest", "Equal to Wave 1", "A corrective wave"), 1),
        QuizQuestion(63, 21, "A key rule of Elliott Wave theory is that Wave 2:",
            listOf("Must be longer than Wave 1", "Can never retrace more than 100% of Wave 1", "Always retraces exactly 50%", "Must be a flat correction"), 1),

        // --- Lesson 22: Volume Profile Analysis ---
        QuizQuestion(64, 22, "The Point of Control (POC) in volume profile represents:",
            listOf("The highest price level", "The price level with the most traded volume", "The closing price", "The average price"), 1),
        QuizQuestion(65, 22, "A 'low volume node' in volume profile suggests:",
            listOf("Strong support/resistance", "Price moved quickly through this area — weak support/resistance", "Heavy accumulation", "Market equilibrium"), 1),
        QuizQuestion(66, 22, "The Value Area in volume profile typically encompasses what percentage of total volume?",
            listOf("50%", "61.8%", "70%", "80%"), 2),

        // --- Lesson 23: Market Profile and Auction Theory ---
        QuizQuestion(67, 23, "In auction market theory, price moves away from value to:",
            listOf("Create chart patterns", "Seek new areas of acceptance where two-sided trade can occur", "Trigger stop losses", "Test indicator signals"), 1),
        QuizQuestion(68, 23, "A 'P-shaped' market profile distribution suggests:",
            listOf("Balanced market", "Long liquidation or short-covering rally — buying above value", "Selling pressure", "Trend continuation down"), 1),
        QuizQuestion(69, 23, "The 'Initial Balance' in market profile refers to:",
            listOf("Your starting account balance", "The price range established in the first hour of trading", "The opening price", "The previous day's range"), 1),

        // --- Lesson 24: Technical Analysis Module Assessment ---
        QuizQuestion(70, 24, "When RSI shows bearish divergence and price is testing a head-and-shoulders neckline, this:",
            listOf("Is meaningless", "Provides confluence of evidence strengthening the bearish case", "Should be ignored in favour of moving averages", "Only matters on weekly charts"), 1),
        QuizQuestion(71, 24, "The most reliable way to confirm a breakout from a chart pattern is:",
            listOf("Checking social media sentiment", "Volume expansion on the breakout candle and follow-through", "Waiting for the next day's close", "RSI crossing 50"), 1),
        QuizQuestion(72, 24, "Multi-timeframe confluence means:",
            listOf("Trading the same asset on all timeframes simultaneously", "Signals from multiple timeframes aligning to support the same directional bias", "Using the same indicator on every chart", "Averaging signals across timeframes"), 1),

        // ========================================================================
        // MODULE 3: FUNDAMENTAL ANALYSIS (Lessons 25-36)
        // ========================================================================

        // --- Lesson 25: Macroeconomic Analysis ---
        QuizQuestion(73, 25, "Rising interest rates typically cause:",
            listOf("Equity prices to rise", "Bond prices to fall as yields rise", "Commodity prices to surge", "No market impact"), 1),
        QuizQuestion(74, 25, "The yield curve inverting (short rates above long rates) historically signals:",
            listOf("Immediate market rally", "Potential economic recession within 6-18 months", "Currency strengthening", "Rising inflation"), 1),
        QuizQuestion(75, 25, "CPI (Consumer Price Index) measures:",
            listOf("Corporate profitability", "The average change in prices paid by consumers for goods and services", "Manufacturing output", "Employment levels"), 1),

        // --- Lesson 26: Financial Statement Analysis ---
        QuizQuestion(76, 26, "A company's free cash flow is calculated as:",
            listOf("Revenue minus expenses", "Operating cash flow minus capital expenditures", "Net income plus depreciation", "Total assets minus liabilities"), 1),
        QuizQuestion(77, 26, "A current ratio below 1.0 indicates:",
            listOf("Strong financial health", "Current liabilities exceed current assets — potential liquidity issues", "High profitability", "Low debt levels"), 1),
        QuizQuestion(78, 26, "The P/E ratio is most useful when:",
            listOf("Used in isolation", "Compared against industry peers and historical averages", "Applied to unprofitable companies", "Calculated using revenue"), 1),

        // --- Lesson 27: Equity Valuation Methods ---
        QuizQuestion(79, 27, "In a DCF (Discounted Cash Flow) analysis, a higher discount rate results in:",
            listOf("A higher present value", "A lower present value — future cash flows are worth less today", "No change in valuation", "Higher projected revenues"), 1),
        QuizQuestion(80, 27, "The terminal value in a DCF typically represents:",
            listOf("First year's cash flow", "The majority of total enterprise value — all cash flows beyond the forecast period", "The liquidation value", "Annual dividend payments"), 1),
        QuizQuestion(81, 27, "Comparable company analysis (comps) values a company by:",
            listOf("Projecting future cash flows", "Applying valuation multiples from similar companies", "Using book value only", "Averaging analyst price targets"), 1),

        // --- Lesson 28: Sector and Industry Analysis ---
        QuizQuestion(82, 28, "Sector rotation theory suggests that in an economic expansion:",
            listOf("Defensive sectors outperform", "Cyclical sectors (technology, consumer discretionary) tend to outperform", "All sectors perform equally", "Only financials benefit"), 1),
        QuizQuestion(83, 28, "Porter's Five Forces analysis evaluates:",
            listOf("Stock price momentum", "The competitive dynamics and attractiveness of an industry", "Macroeconomic conditions", "Technical chart patterns"), 1),
        QuizQuestion(84, 28, "A high barrier to entry in an industry benefits:",
            listOf("New entrants", "Existing competitors by limiting new competition", "Consumers through lower prices", "Regulators"), 1),

        // --- Lesson 29: Cryptocurrency Fundamental Analysis ---
        QuizQuestion(85, 29, "Token economics ('tokenomics') analysis should prioritise:",
            listOf("The token's logo and branding", "Supply schedule, distribution, utility, and incentive mechanisms", "Celebrity endorsements", "Current market cap only"), 1),
        QuizQuestion(86, 29, "The MVRV Z-Score compares:",
            listOf("Trading volume to average", "Market value to realised value to assess over/undervaluation", "Network difficulty to hash rate", "Active addresses to total supply"), 1),
        QuizQuestion(87, 29, "When analysing a crypto project's whitepaper, the most critical section is:",
            listOf("The team photos", "The technical architecture, consensus mechanism, and economic model", "Price predictions", "Marketing roadmap"), 1),

        // --- Lesson 30: DeFi Protocol Analysis ---
        QuizQuestion(88, 30, "Total Value Locked (TVL) in a DeFi protocol measures:",
            listOf("The total market cap of the token", "The total value of assets deposited into the protocol's smart contracts", "Daily trading volume", "The number of users"), 1),
        QuizQuestion(89, 30, "Impermanent loss in an AMM liquidity pool occurs when:",
            listOf("The protocol is hacked", "The price ratio of deposited tokens changes relative to when they were deposited", "Gas fees are too high", "Too many users deposit funds"), 1),
        QuizQuestion(90, 30, "When evaluating a DeFi protocol's security, the most important factor is:",
            listOf("Token price performance", "Comprehensive smart contract audits by reputable firms and bug bounty programmes", "Social media following", "The founding team's country"), 1),

        // --- Lesson 31: News and Sentiment Analysis ---
        QuizQuestion(91, 31, "The Fear & Greed Index reading of 10 (Extreme Fear) historically correlates with:",
            listOf("Market tops", "Potential buying opportunities as markets are oversold", "Neutral market conditions", "Rising interest rates"), 1),
        QuizQuestion(92, 31, "The phrase 'buy the rumour, sell the news' reflects:",
            listOf("Insider trading strategies", "The tendency for prices to rise on anticipation and fall when the event actually occurs", "A fundamental analysis approach", "Technical pattern trading"), 1),
        QuizQuestion(93, 31, "Sentiment analysis is most useful as a:",
            listOf("Standalone trading strategy", "Contrarian indicator when combined with technical and fundamental analysis", "Replacement for risk management", "Daily trading signal"), 1),

        // --- Lesson 32: Earnings Analysis and Trading ---
        QuizQuestion(94, 32, "An 'earnings surprise' is measured relative to:",
            listOf("The company's guidance only", "Analyst consensus estimates", "Previous quarter's earnings", "Revenue targets"), 1),
        QuizQuestion(95, 32, "Implied volatility typically behaves around earnings announcements by:",
            listOf("Staying constant", "Rising before the announcement and collapsing afterward (IV crush)", "Falling before and rising after", "Following the stock price direction"), 1),
        QuizQuestion(96, 32, "Forward guidance from management is important because:",
            listOf("It legally guarantees future performance", "It shapes analyst expectations and future earnings estimates", "It determines dividend payments", "It affects only bond prices"), 1),

        // --- Lesson 33: Global Macro Trading ---
        QuizQuestion(97, 33, "A global macro trader primarily makes decisions based on:",
            listOf("Individual stock analysis", "Broad economic trends, policy changes, and geopolitical developments", "Technical chart patterns only", "Social media trends"), 1),
        QuizQuestion(98, 33, "Carry trade strategies involve:",
            listOf("Buying and holding blue-chip stocks", "Borrowing in a low-interest-rate currency to invest in a higher-yielding one", "Buying commodities during inflation", "Shorting volatility"), 1),
        QuizQuestion(99, 33, "When central banks engage in quantitative easing (QE), the typical effect on asset prices is:",
            listOf("Deflationary pressure on equities", "Generally supportive — increased liquidity flows into financial assets", "No impact on markets", "Immediate currency appreciation"), 1),

        // --- Lesson 34: Quantitative Fundamental Analysis ---
        QuizQuestion(100, 34, "Factor investing focuses on:",
            listOf("Single stock selection", "Systematic exposure to characteristics (value, momentum, quality) that explain returns", "Market timing", "Index tracking only"), 1),
        QuizQuestion(101, 34, "The Fama-French three-factor model extends CAPM by adding:",
            listOf("Volatility and momentum", "Company size (SMB) and value (HML) factors", "Sentiment and volume", "Technical and fundamental scores"), 1),
        QuizQuestion(102, 34, "Backtesting a quantitative fundamental strategy requires caution against:",
            listOf("Using too much data", "Look-ahead bias — using information that wasn't available at the time", "Testing on multiple assets", "Including transaction costs"), 1),

        // --- Lesson 35: Intermarket Relationships ---
        QuizQuestion(103, 35, "Historically, the US Dollar and gold prices tend to have:",
            listOf("A strong positive correlation", "An inverse relationship — dollar strength pressures gold", "No correlation", "The same trend at all times"), 1),
        QuizQuestion(104, 35, "Rising bond yields (falling bond prices) typically signal:",
            listOf("Lower inflation expectations", "Tightening financial conditions that may pressure equities", "Weaker economic growth", "Central bank easing"), 1),
        QuizQuestion(105, 35, "The DXY (US Dollar Index) measures the dollar against:",
            listOf("All world currencies equally", "A basket of six major currencies weighted by trade", "Only the Euro", "Cryptocurrency"), 1),

        // --- Lesson 36: Fundamental Analysis Module Assessment ---
        QuizQuestion(106, 36, "A crypto token with strong on-chain metrics but extreme fear sentiment might represent:",
            listOf("An asset to avoid completely", "A potential contrarian opportunity where fundamentals diverge from emotion", "Confirmation of bearish fundamentals", "A signal to follow the crowd"), 1),
        QuizQuestion(107, 36, "When a company's P/E is 30 while its industry average is 15, this could indicate:",
            listOf("The stock is definitely overvalued", "High growth expectations, overvaluation, or premium quality — requires deeper analysis", "The company will definitely outperform", "An accounting error"), 1),
        QuizQuestion(108, 36, "The strongest fundamental signal occurs when:",
            listOf("Price is rising quickly", "Multiple fundamental, technical, and sentiment indicators converge on the same conclusion", "A single metric shows extreme readings", "Analysts unanimously agree"), 1),

        // ========================================================================
        // MODULE 4: RISK MANAGEMENT (Lessons 37-48)
        // ========================================================================

        // --- Lesson 37: Kelly Criterion and Optimal Betting ---
        QuizQuestion(109, 37, "The Kelly Criterion calculates:",
            listOf("Maximum leverage allowed", "The optimal fraction of capital to risk on a trade for maximum geometric growth", "The number of trades per day", "Minimum position size"), 1),
        QuizQuestion(110, 37, "Fractional Kelly (e.g., quarter Kelly at 0.25) is preferred because:",
            listOf("It maximises absolute returns", "It significantly reduces volatility and drawdown risk at modest cost to returns", "It is required by regulators", "It eliminates all risk"), 1),
        QuizQuestion(111, 37, "If win rate is 60% and average win/loss ratio is 2:1, the full Kelly fraction is approximately:",
            listOf("10%", "20%", "40%", "60%"), 2),

        // --- Lesson 38: Value at Risk (VaR) ---
        QuizQuestion(112, 38, "A 95% daily VaR of AU$10,000 means:",
            listOf("You will lose exactly AU$10,000 daily", "On 95% of trading days, losses should not exceed AU$10,000", "Maximum possible loss is AU$10,000", "Average daily loss is AU$10,000"), 1),
        QuizQuestion(113, 38, "Expected Shortfall (CVaR) improves on VaR by:",
            listOf("Being easier to calculate", "Measuring the average loss in the worst cases beyond the VaR threshold", "Ignoring tail risk", "Using shorter time periods"), 1),
        QuizQuestion(114, 38, "The main limitation of historical VaR is:",
            listOf("It uses too much data", "It assumes past distributions will persist — may underestimate tail risk", "It overestimates risk", "It cannot handle multiple assets"), 1),

        // --- Lesson 39: Correlation and Diversification ---
        QuizQuestion(115, 39, "A correlation of -1.0 between two assets means:",
            listOf("They move in exactly the same direction", "They move in perfectly opposite directions", "They are unrelated", "One is more volatile"), 1),
        QuizQuestion(116, 39, "During market crises, correlations between assets tend to:",
            listOf("Decrease to zero", "Increase toward +1.0, reducing diversification benefits", "Become negative", "Remain stable"), 1),
        QuizQuestion(117, 39, "True portfolio diversification requires assets that:",
            listOf("Are all in the same asset class", "Have low or negative correlation during both normal and stressed conditions", "Have identical risk profiles", "Are traded on the same exchange"), 1),

        // --- Lesson 40: Portfolio Optimisation ---
        QuizQuestion(118, 40, "The Efficient Frontier represents:",
            listOf("The most popular trading strategies", "The set of portfolios offering maximum expected return for each level of risk", "A single optimal portfolio", "Government bond yields"), 1),
        QuizQuestion(119, 40, "Risk parity portfolio construction allocates based on:",
            listOf("Equal dollar amounts", "Equal risk contribution from each asset", "Market capitalisation weighting", "Historical returns"), 1),
        QuizQuestion(120, 40, "The Sharpe ratio is calculated as:",
            listOf("Return divided by maximum drawdown", "(Portfolio return minus risk-free rate) divided by portfolio standard deviation", "Win rate times average win", "Total return divided by number of trades"), 1),

        // --- Lesson 41: Drawdown Management ---
        QuizQuestion(121, 41, "Maximum drawdown measures:",
            listOf("The largest single-day loss", "The largest peak-to-trough decline in portfolio value", "Average daily loss", "Total cumulative losses"), 1),
        QuizQuestion(122, 41, "After a 50% drawdown, the return required to recover to breakeven is:",
            listOf("50%", "75%", "100%", "150%"), 2),
        QuizQuestion(123, 41, "Progressive position size reduction during drawdowns (e.g., STAHL Stair Stop™) works by:",
            listOf("Increasing risk to recover faster", "Locking in progressive profit percentages to protect against giving back gains", "Eliminating all trading during drawdowns", "Doubling down on losing positions"), 1),

        // --- Lesson 42: Tail Risk and Black Swan Events ---
        QuizQuestion(124, 42, "A 'Black Swan' event is characterised by:",
            listOf("Predictable outcomes", "Extreme rarity, severe impact, and retrospective predictability", "Moderate market moves", "Events that occur monthly"), 1),
        QuizQuestion(125, 42, "The best defence against tail risk is:",
            listOf("Ignoring unlikely events", "Position sizing, diversification, and defined maximum loss limits (kill switch)", "Concentration in safe assets only", "Relying on VaR models"), 1),
        QuizQuestion(126, 42, "Fat tails in return distributions mean that:",
            listOf("Markets are perfectly efficient", "Extreme events occur more frequently than a normal distribution would predict", "Volatility is always low", "Returns are evenly distributed"), 1),

        // --- Lesson 43: Leverage and Margin Management ---
        QuizQuestion(127, 43, "Using 10x leverage on a AU$10,000 account means:",
            listOf("You can trade AU$100,000 in positions but a 10% adverse move wipes out your entire capital", "Your maximum loss is AU$1,000", "You earn 10x more on every trade", "Risk is reduced by 10x"), 0),
        QuizQuestion(128, 43, "A margin call occurs when:",
            listOf("You make a profitable trade", "Account equity falls below the maintenance margin requirement", "You deposit additional funds", "The exchange closes for the day"), 1),
        QuizQuestion(129, 43, "The optimal leverage for a conservative strategy (Kelly quarter) is typically:",
            listOf("50x-100x", "10x-25x", "1x-3x", "No leverage"), 2),

        // --- Lesson 44: Hedging Strategies ---
        QuizQuestion(130, 44, "A delta-neutral hedge aims to:",
            listOf("Maximise directional exposure", "Eliminate sensitivity to small price moves in the underlying asset", "Increase leverage", "Avoid all trading"), 1),
        QuizQuestion(131, 44, "Hedging a long equity portfolio with put options:",
            listOf("Eliminates all risk for free", "Provides downside protection at the cost of the option premium", "Increases upside potential", "Is illegal in most jurisdictions"), 1),
        QuizQuestion(132, 44, "Funding rate arbitrage in perpetual futures involves:",
            listOf("Speculating on funding rate direction", "Taking offsetting spot and perpetual positions to capture the funding rate differential", "Only trading during positive funding", "Borrowing at low rates to invest"), 1),

        // --- Lesson 45: Liquidity Risk Management ---
        QuizQuestion(133, 45, "Slippage is most likely to be significant when:",
            listOf("Trading large positions in highly liquid markets", "Trading large positions relative to available market depth", "Placing limit orders", "Trading during normal hours"), 1),
        QuizQuestion(134, 45, "Market depth refers to:",
            listOf("The total number of traders", "The volume of orders at various price levels in the order book", "Historical price data length", "The number of exchanges listing an asset"), 1),
        QuizQuestion(135, 45, "TWAP (Time-Weighted Average Price) execution is designed to:",
            listOf("Execute immediately at market price", "Spread a large order over time to minimise market impact", "Get the best possible price instantly", "Trade only at the close"), 1),

        // --- Lesson 46: Counterparty and Operational Risk ---
        QuizQuestion(136, 46, "Non-custodial (self-sovereign) trading eliminates which risk?",
            listOf("Market risk", "Platform custodial risk — your funds cannot be frozen or misappropriated", "Execution risk", "Strategy risk"), 1),
        QuizQuestion(137, 46, "The FTX collapse in 2022 was primarily a failure of:",
            listOf("Technical infrastructure", "Custodial and operational risk — commingling of customer funds", "Market timing", "Too much regulation"), 1),
        QuizQuestion(138, 46, "MPC (Multi-Party Computation) wallet technology reduces risk by:",
            listOf("Storing all keys in one location", "Splitting signing authority across multiple parties so no single party can access funds alone", "Using a central server", "Eliminating the need for private keys"), 1),

        // --- Lesson 47: Risk Reporting and Monitoring ---
        QuizQuestion(139, 47, "The Sortino ratio improves on the Sharpe ratio by:",
            listOf("Using a longer time period", "Only penalising downside volatility rather than all volatility", "Including transaction costs", "Measuring maximum drawdown"), 1),
        QuizQuestion(140, 47, "Real-time risk monitoring should trigger alerts when:",
            listOf("Profits exceed targets", "Position concentration, drawdown, or correlation thresholds are breached", "Markets are closed", "Volume is average"), 1),
        QuizQuestion(141, 47, "A kill switch that triggers at 5% daily drawdown is designed to:",
            listOf("Maximise daily profits", "Prevent catastrophic losses by automatically halting all trading", "Close only profitable positions", "Send a notification only"), 1),

        // --- Lesson 48: Risk Management Module Assessment ---
        QuizQuestion(142, 48, "A portfolio using quarter Kelly sizing with a 15% maximum drawdown kill switch demonstrates:",
            listOf("Excessive caution", "Layered risk management — sizing limits exposure and the kill switch caps worst case", "Contradictory approaches", "Only institutional-grade risk management"), 1),
        QuizQuestion(143, 48, "The STAHL Stair Stop™ system's primary advantage over a standard trailing stop is:",
            listOf("It's simpler to implement", "It locks in progressive percentages of profit at defined levels rather than trailing at a fixed distance", "It eliminates all losses", "It only works in bull markets"), 1),
        QuizQuestion(144, 48, "In a self-sovereign trading platform, risk management responsibility ultimately lies with:",
            listOf("The platform operator", "The user — who configures and controls all risk parameters on their own device", "Regulators", "The exchange"), 1),

        // ========================================================================
        // MODULE 5: ADVANCED STRATEGIES (Lessons 49-60)
        // ========================================================================

        // --- Lesson 49: Momentum Trading Strategies ---
        QuizQuestion(145, 49, "Momentum strategies are based on the observation that:",
            listOf("Markets are perfectly random", "Assets that have performed well recently tend to continue performing well in the near term", "Mean reversion always dominates", "Only fundamentals drive prices"), 1),
        QuizQuestion(146, 49, "The primary risk of momentum trading is:",
            listOf("Too many small losses", "Momentum crashes — sudden, sharp reversals that erase gains", "Missing fundamental shifts", "High transaction costs only"), 1),
        QuizQuestion(147, 49, "Rate of Change (ROC) indicator measures:",
            listOf("Volume change", "The percentage change in price over a specified period", "Trend direction only", "Volatility"), 1),

        // --- Lesson 50: Mean Reversion Strategies ---
        QuizQuestion(148, 50, "Mean reversion strategies profit when:",
            listOf("Trends continue indefinitely", "Prices that have deviated from a mean or fair value revert back toward it", "Markets crash", "Only in bull markets"), 1),
        QuizQuestion(149, 50, "Bollinger Band mean reversion trades typically enter when:",
            listOf("Price breaks above the upper band", "Price touches or exceeds a band and then reverses back inside", "Bands are expanding rapidly", "Volume is increasing"), 1),
        QuizQuestion(150, 50, "The main risk of mean reversion in crypto markets is:",
            listOf("Too many winning trades", "Regime change — markets can trend for extended periods, preventing mean reversion", "Low volatility", "High liquidity"), 1),

        // --- Lesson 51: Breakout Trading Strategies ---
        QuizQuestion(151, 51, "A valid breakout is best confirmed by:",
            listOf("Price moving 1 tick beyond the level", "Volume expansion, candle close beyond the level, and follow-through", "Social media mentions", "A single analyst recommendation"), 1),
        QuizQuestion(152, 51, "A false breakout (fakeout) can be used as a trading signal by:",
            listOf("Ignoring it", "Trading in the opposite direction of the failed breakout with a stop beyond the fakeout high/low", "Doubling down on the breakout direction", "Waiting for the next breakout"), 1),
        QuizQuestion(153, 51, "The ideal entry for a breakout trade is:",
            listOf("Before the breakout occurs", "On the initial breakout candle or on a pullback/retest of the broken level", "Days after the breakout", "Only at market open"), 1),

        // --- Lesson 52: Scalping and High-Frequency Concepts ---
        QuizQuestion(154, 52, "Scalping strategies typically target:",
            listOf("Large multi-day moves", "Small, rapid price movements with high frequency and tight risk management", "Weekly chart patterns", "Fundamental value shifts"), 1),
        QuizQuestion(155, 52, "The most critical factor for scalping profitability is:",
            listOf("High win rate strategy", "Low transaction costs — fees and slippage must be minimal relative to targets", "Fundamental analysis", "Long holding periods"), 1),
        QuizQuestion(156, 52, "Market making as a high-frequency strategy profits primarily from:",
            listOf("Directional price moves", "Capturing the bid-ask spread by providing liquidity on both sides", "News events", "Weekend gaps"), 1),

        // --- Lesson 53: Swing Trading Mastery ---
        QuizQuestion(157, 53, "Swing trading typically holds positions for:",
            listOf("Seconds to minutes", "Several days to a few weeks, capturing medium-term price swings", "Several months to years", "Intraday only"), 1),
        QuizQuestion(158, 53, "The ideal swing trading setup combines:",
            listOf("Only one indicator", "Higher-timeframe trend alignment with lower-timeframe entry trigger at key levels", "Random entry with tight stops", "Fundamental analysis only"), 1),
        QuizQuestion(159, 53, "Swing traders manage risk differently from scalpers by:",
            listOf("Using no stops", "Using wider stops with smaller position sizes to accommodate multi-day holding", "Taking larger positions", "Ignoring overnight gaps"), 1),

        // --- Lesson 54: Position Trading and Investing ---
        QuizQuestion(160, 54, "Position trading differs from swing trading primarily in:",
            listOf("The indicators used", "Holding period — weeks to months, focusing on major trends and macro themes", "The assets traded", "Risk management approach"), 1),
        QuizQuestion(161, 54, "Dollar-cost averaging (DCA) is an effective strategy for:",
            listOf("Short-term trading", "Reducing timing risk by spreading purchases over time at regular intervals", "Maximising leverage", "Avoiding all market risk"), 1),
        QuizQuestion(162, 54, "The Australian 50% CGT discount applies to assets held for:",
            listOf("More than 6 months", "More than 12 months", "More than 3 years", "Any holding period"), 1),

        // --- Lesson 55: Options Strategies ---
        QuizQuestion(163, 55, "A covered call strategy involves:",
            listOf("Buying a call option only", "Holding the underlying asset while selling a call option against it for premium income", "Selling naked calls", "Buying puts for protection"), 1),
        QuizQuestion(164, 55, "Options theta (time decay) works in favour of:",
            listOf("Option buyers", "Option sellers — options lose value as expiration approaches", "Neither party", "Only at-the-money options"), 1),
        QuizQuestion(165, 55, "An iron condor is designed to profit when:",
            listOf("Markets move sharply in one direction", "The underlying asset stays within a defined price range until expiration", "Volatility increases dramatically", "A trend continues"), 1),

        // --- Lesson 56: Arbitrage Strategies ---
        QuizQuestion(166, 56, "Triangular arbitrage in forex exploits:",
            listOf("Interest rate differentials", "Temporary pricing inefficiencies between three currency pairs", "Trend momentum", "News events"), 1),
        QuizQuestion(167, 56, "Statistical arbitrage differs from pure arbitrage in that it:",
            listOf("Is risk-free", "Uses statistical models and carries some risk of the relationship breaking down", "Requires no capital", "Only works in crypto"), 1),
        QuizQuestion(168, 56, "Cross-exchange arbitrage in crypto is possible because:",
            listOf("All exchanges have identical prices", "Price discrepancies exist between exchanges due to fragmented liquidity", "Regulators set different prices", "Blockchain confirmations are instant"), 1),

        // --- Lesson 57: Market Making Concepts ---
        QuizQuestion(169, 57, "A market maker's primary revenue source is:",
            listOf("Directional bets on price", "The bid-ask spread captured by quoting on both sides of the order book", "Subscription fees", "Interest on deposits"), 1),
        QuizQuestion(170, 57, "Adverse selection risk for market makers occurs when:",
            listOf("Markets are calm", "Informed traders trade against the market maker's quotes, leading to consistent losses", "Spreads are wide", "Volume is high"), 1),
        QuizQuestion(171, 57, "Inventory management for market makers involves:",
            listOf("Holding maximum inventory at all times", "Actively balancing long and short exposure to minimise directional risk", "Only quoting one side", "Ignoring position size"), 1),

        // --- Lesson 58: Algorithmic Trading Introduction ---
        QuizQuestion(172, 58, "The primary advantage of algorithmic trading over manual trading is:",
            listOf("It always makes money", "Elimination of emotional bias, consistent execution, and ability to process data at scale", "It requires no strategy", "It is unregulated"), 1),
        QuizQuestion(173, 58, "Overfitting in algorithmic strategy development means:",
            listOf("The strategy works perfectly in all markets", "The model has learned noise in historical data rather than genuine patterns, and will fail on new data", "Using too little data", "The algorithm is too simple"), 1),
        QuizQuestion(174, 58, "Walk-forward analysis helps prevent overfitting by:",
            listOf("Using the entire dataset at once", "Testing the strategy on sequential out-of-sample periods after each optimisation window", "Removing all parameters", "Only testing on one asset"), 1),

        // --- Lesson 59: Strategy Combination and Portfolio ---
        QuizQuestion(175, 59, "Combining uncorrelated strategies in a portfolio primarily benefits:",
            listOf("Absolute returns only", "Risk-adjusted returns through diversification of strategy risk", "Win rate", "Maximum position size"), 1),
        QuizQuestion(176, 59, "An ensemble approach using multiple AI models (e.g., trend, momentum, sentiment, technical) works because:",
            listOf("One model is always right", "Different models capture different market dynamics, and consensus improves accuracy", "Models cancel each other out", "It requires less data"), 1),
        QuizQuestion(177, 59, "When two strategies produce conflicting signals, the best approach is to:",
            listOf("Follow the more recent signal", "Reduce position size or stand aside — uncertainty warrants caution", "Double the position", "Ignore both signals"), 1),

        // --- Lesson 60: Advanced Strategies Module Assessment ---
        QuizQuestion(178, 60, "A trader using momentum entries with STAHL Stair Stop™ exits is combining:",
            listOf("Two unrelated approaches", "Trend-following entries with progressive profit-locking exits for asymmetric returns", "Mean reversion with scalping", "Fundamental and technical analysis"), 1),
        QuizQuestion(179, 60, "The AI Board consensus mechanism adds value to strategy execution by:",
            listOf("Replacing all human judgment", "Providing multi-perspective analysis where weighted voting filters low-confidence signals", "Guaranteeing profitable trades", "Only approving short trades"), 1),
        QuizQuestion(180, 60, "In backtesting, the most common mistake that inflates results is:",
            listOf("Using too many assets", "Survivorship bias, look-ahead bias, and not accounting for transaction costs and slippage", "Testing on too long a period", "Including losing trades"), 1),

        // ========================================================================
        // MODULE 6: INSTITUTIONAL METHODS (Lessons 61-72)
        // ========================================================================

        // --- Lesson 61: Institutional Order Flow Analysis ---
        QuizQuestion(181, 61, "Institutional order flow can be detected by:",
            listOf("Following social media influencers", "Unusual volume at key levels, large block trades, and order book imbalances", "Checking company earnings", "Reading press releases"), 1),
        QuizQuestion(182, 61, "Dark pool transactions are significant because:",
            listOf("They are illegal", "Large orders executed off-exchange can indicate institutional positioning before it appears in public data", "They always move prices", "They are only for retail traders"), 1),
        QuizQuestion(183, 61, "Cumulative Volume Delta (CVD) reveals:",
            listOf("Total volume only", "The net difference between buying and selling pressure over time", "The number of trades", "Spread width"), 1),

        // --- Lesson 62: Smart Money Concepts ---
        QuizQuestion(184, 62, "A 'liquidity grab' or 'stop hunt' pattern occurs when:",
            listOf("Markets are perfectly efficient", "Price briefly moves beyond an obvious level to trigger stops before reversing in the opposite direction", "Institutions add to positions gradually", "Volume is low"), 1),
        QuizQuestion(185, 62, "An 'order block' in smart money analysis is:",
            listOf("A blocked order", "A zone where institutional buying or selling initiated a significant move — likely to act as future S/R", "A chart pattern", "An indicator signal"), 1),
        QuizQuestion(186, 62, "Fair value gaps (FVGs) represent:",
            listOf("Fair market value", "Imbalanced price action where one side dominated, creating inefficiency that may be revisited", "Gaps between exchanges", "The difference between bid and ask"), 1),

        // --- Lesson 63: Wyckoff Method ---
        QuizQuestion(187, 63, "The Wyckoff accumulation phase is characterised by:",
            listOf("Rapidly rising prices", "A trading range where institutional operators gradually build positions before a markup phase", "Heavy selling pressure", "High volatility breakouts"), 1),
        QuizQuestion(188, 63, "The 'spring' in Wyckoff methodology refers to:",
            listOf("A pattern that occurs in spring months", "A brief penetration below support that quickly reverses — a bullish sign of accumulation completion", "A sharp rally", "Volume expansion"), 1),
        QuizQuestion(189, 63, "Wyckoff's 'Composite Operator' concept represents:",
            listOf("A single large trader", "The aggregate behaviour of well-informed institutional participants acting in their own interest", "Retail trader consensus", "Automated trading bots"), 1),

        // --- Lesson 64: Intermarket Analysis ---
        QuizQuestion(190, 64, "The traditional intermarket relationship between bonds and stocks is:",
            listOf("Always positively correlated", "Generally inverse — rising bond yields (falling prices) can pressure equities", "Completely unrelated", "Bonds always lead stocks"), 1),
        QuizQuestion(191, 64, "Commodity prices rising while the US dollar weakens typically signals:",
            listOf("Deflationary conditions", "Inflationary pressures and potential risk-on environment", "Economic recession", "Currency crisis"), 1),
        QuizQuestion(192, 64, "Bitcoin's correlation with traditional risk assets has:",
            listOf("Always been zero", "Increased during macro stress events, reducing its diversification benefit", "Always been negative", "Remained perfectly stable"), 1),

        // --- Lesson 65: Quantitative Strategy Development ---
        QuizQuestion(193, 65, "A robust quantitative strategy should demonstrate:",
            listOf("Perfect backtesting results", "Consistent performance across multiple assets, timeframes, and market regimes", "Maximum complexity", "Optimisation on every parameter"), 1),
        QuizQuestion(194, 65, "The 'deflated Sharpe ratio' accounts for:",
            listOf("Inflation", "Multiple testing bias — adjusting for the number of strategies tested to find the winner", "Leverage effects", "Tax implications"), 1),
        QuizQuestion(195, 65, "Monte Carlo simulation in strategy development helps assess:",
            listOf("Exact future returns", "The range of possible outcomes by randomising trade sequences and parameters", "Optimal entry points", "Market direction"), 1),

        // --- Lesson 66: Machine Learning in Trading ---
        QuizQuestion(196, 66, "Deep Q-Networks (DQN) in trading learn to:",
            listOf("Predict exact prices", "Select optimal actions (buy/sell/hold) by learning a value function from experience replay", "Generate news articles", "Replace all other analysis"), 1),
        QuizQuestion(197, 66, "The primary challenge of applying machine learning to financial markets is:",
            listOf("Insufficient computing power", "Non-stationarity — market dynamics change over time, causing model degradation", "Too much available data", "Simple patterns"), 1),
        QuizQuestion(198, 66, "Elastic Weight Consolidation (EWC) prevents:",
            listOf("Overfitting to recent data", "Catastrophic forgetting — preserving important learned weights when training on new data", "Model complexity", "Gradient explosion"), 1),

        // --- Lesson 67: Execution Optimisation ---
        QuizQuestion(199, 67, "Implementation shortfall measures:",
            listOf("The difference between paper and live results", "The total cost of executing a trade versus the theoretical ideal price at decision time", "Slippage only", "Commission costs"), 1),
        QuizQuestion(200, 67, "Smart order routing (SOR) improves execution by:",
            listOf("Always using the fastest exchange", "Dynamically routing orders across venues to achieve best available price and liquidity", "Delaying all orders", "Only trading on one exchange"), 1),
        QuizQuestion(201, 67, "VWAP (Volume-Weighted Average Price) execution benchmarks against:",
            listOf("The opening price", "The volume-weighted average price throughout the trading session", "The closing price only", "The previous day's close"), 1),

        // --- Lesson 68: Portfolio Construction for Institutions ---
        QuizQuestion(202, 68, "Institutional portfolio construction typically begins with:",
            listOf("Stock selection", "Strategic asset allocation — defining target weightings across asset classes", "Technical analysis", "Market timing"), 1),
        QuizQuestion(203, 68, "Rebalancing a portfolio to target weights is important because:",
            listOf("It maximises returns", "It maintains the intended risk profile as asset values diverge over time", "It reduces transaction costs", "It is required by law"), 1),
        QuizQuestion(204, 68, "A core-satellite portfolio approach combines:",
            listOf("All passive investments", "A passive core allocation with active satellite positions for alpha generation", "Only hedge fund strategies", "Individual stock picking"), 1),

        // --- Lesson 69: Performance Attribution ---
        QuizQuestion(205, 69, "Performance attribution analysis separates returns into:",
            listOf("Winning and losing trades only", "Components like asset allocation, security selection, and market timing to identify value-add sources", "Gross and net returns", "Risk and reward"), 1),
        QuizQuestion(206, 69, "Alpha represents:",
            listOf("Total portfolio return", "Return above what would be expected given the portfolio's systematic risk exposure (beta)", "The risk-free rate", "Market return"), 1),
        QuizQuestion(207, 69, "The Calmar ratio measures:",
            listOf("Return per unit of volatility", "Annualised return divided by maximum drawdown — reward per unit of worst-case pain", "Win rate to loss rate", "Sharpe ratio over time"), 1),

        // --- Lesson 70: Regulatory Compliance and Ethics ---
        QuizQuestion(208, 70, "A non-custodial trading platform avoids financial services licensing because:",
            listOf("It operates offshore", "It never takes custody of client funds — users maintain control via their own exchange accounts", "It has fewer than 100 users", "It only trades crypto"), 1),
        QuizQuestion(209, 70, "Market manipulation includes all EXCEPT:",
            listOf("Wash trading", "Spoofing orders", "Placing a legitimate limit order based on your own analysis", "Front-running client orders"), 2),
        QuizQuestion(210, 70, "Under Australian tax law, cryptocurrency is treated as:",
            listOf("Currency", "Property subject to capital gains tax", "Tax-exempt", "Foreign income"), 1),

        // --- Lesson 71: Trading Business Management ---
        QuizQuestion(211, 71, "A professional trading operation should track costs including:",
            listOf("Only exchange fees", "Exchange fees, slippage, funding costs, software costs, and opportunity cost of capital", "Only profitable trades", "Tax obligations only"), 1),
        QuizQuestion(212, 71, "Position sizing should be recalculated:",
            listOf("Once per year", "Before each trade based on current portfolio value, risk parameters, and market conditions", "Only after losses", "Never — use fixed sizes"), 1),
        QuizQuestion(213, 71, "The most important metric for a trading business is:",
            listOf("Win rate alone", "Risk-adjusted return (Sharpe/Sortino) relative to maximum drawdown experienced", "Number of trades", "Gross revenue"), 1),

        // --- Lesson 72: Institutional Methods Module Assessment ---
        QuizQuestion(214, 72, "An AI Board of Directors governance model improves trading decisions by:",
            listOf("Replacing all human oversight", "Providing multi-perspective weighted analysis with consensus thresholds and XAI audit trails", "Guaranteeing profits", "Eliminating the need for risk management"), 1),
        QuizQuestion(215, 72, "Self-sovereign architecture is essential for HNW clients because:",
            listOf("It's cheaper to operate", "It eliminates custodial risk, ensures privacy, and gives complete control over fund management", "It requires less knowledge", "It avoids all taxes"), 1),
        QuizQuestion(216, 72, "DHT (Distributed Hash Table) federated learning benefits users by:",
            listOf("Sharing individual trade data", "Allowing AI models to improve from network-wide patterns without exposing any user's private data", "Centralising all computations", "Requiring user approval for each update"), 1),

        // ========================================================================
        // MODULE 7: MASTERY (Lessons 73-76)
        // ========================================================================

        // --- Lesson 73: Comprehensive Knowledge Review ---
        QuizQuestion(217, 73, "The three pillars of a complete trading system are:",
            listOf("Speed, leverage, and volume", "Entry signals, risk management, and exit strategy with position sizing", "Fundamental analysis, technical analysis, and luck", "Backtesting, live trading, and marketing"), 1),
        QuizQuestion(218, 73, "Post-quantum cryptography in a trading platform protects against:",
            listOf("Current hacking only", "Future quantum computers that could break current encryption, ensuring long-term security of keys and data", "Regulatory compliance", "Network latency"), 1),
        QuizQuestion(219, 73, "The Kelly Criterion, STAHL Stair Stop™, and AI Board consensus represent layers of:",
            listOf("Profit maximisation", "Defence-in-depth — optimal sizing, progressive profit protection, and intelligent signal filtering", "Unnecessary complexity", "Marketing features"), 1),

        // --- Lesson 74: Live Trading Simulation ---
        QuizQuestion(220, 74, "During a live simulation, the most critical skill being tested is:",
            listOf("Speed of execution", "The ability to follow your trading plan under realistic market pressure", "Making the most trades possible", "Predicting exact price targets"), 1),
        QuizQuestion(221, 74, "If a live simulation produces results significantly worse than backtests, the likely cause is:",
            listOf("Bad luck", "Execution differences (slippage, emotional decision-making) and potential backtesting overfitting", "The market changed permanently", "Technical issues only"), 1),
        QuizQuestion(222, 74, "The purpose of paper trading before going live is to:",
            listOf("Make money risk-free", "Validate strategy execution, identify technical issues, and build confidence without capital risk", "Impress investors", "Meet regulatory requirements"), 1),

        // --- Lesson 75: Final Written Examination ---
        QuizQuestion(223, 75, "A complete trading infrastructure should include:",
            listOf("Only a trading strategy", "Strategy, risk management, execution infrastructure, monitoring, and compliance documentation", "Just an exchange account", "A social media following"), 1),
        QuizQuestion(224, 75, "The fundamental principle of Sovereign Vantage's architecture is:",
            listOf("Centralised efficiency", "Self-sovereignty — all processing, keys, and decisions remain on the user's device", "Cloud-first design", "Maximum data collection"), 1),
        QuizQuestion(225, 75, "When multiple risk systems conflict (e.g., Kelly says trade, kill switch is near trigger), the correct action is:",
            listOf("Follow Kelly — it's mathematically optimal", "Respect the most conservative constraint — the kill switch protects capital first", "Average the signals", "Override both and trade based on intuition"), 1),

        // --- Lesson 76: Master Trader Certification ---
        QuizQuestion(226, 76, "A Master Trader certification from Sovereign Vantage demonstrates:",
            listOf("Guaranteed future profitability", "Comprehensive understanding of markets, risk management, strategy, and institutional methods", "That the holder can predict markets", "Regulatory approval to trade"), 1),
        QuizQuestion(227, 76, "The most valuable trait of a successful trader is:",
            listOf("Aggressive risk-taking", "Disciplined execution of a well-tested system with continuous learning and adaptation", "Always being right", "Trading the most volatile assets"), 1),
        QuizQuestion(228, 76, "Generational wealth through active investment management requires:",
            listOf("One large winning trade", "Consistent risk-adjusted returns, capital preservation, and a sovereign approach to wealth management", "Maximum leverage at all times", "Following market trends blindly"), 1)
    )
}
