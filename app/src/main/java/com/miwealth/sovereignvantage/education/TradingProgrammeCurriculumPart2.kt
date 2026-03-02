/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - CURRICULUM DATA
 * 
 * Part 2: Modules 4-7 (Risk Management, Advanced Strategies, Institutional Methods, Mastery)
 * Lessons 37-76
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */


/**
 * Continuation of the 76-lesson curriculum.
 * Modules 4-7 covering advanced topics and certification.
 */
package com.miwealth.sovereignvantage.education

object TradingProgrammeCurriculumPart2 {

    // ========================================================================
    // MODULE 4: ADVANCED RISK MANAGEMENT (Lessons 37-48)
    // ========================================================================
    
    private val module4Lessons = listOf(
        Lesson(
            id = 37,
            moduleId = 4,
            title = "Kelly Criterion and Optimal Betting",
            description = "Mathematical approach to position sizing using Kelly Criterion",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(8, 12),
            objectives = listOf(
                "Understand Kelly Criterion mathematics",
                "Apply fractional Kelly for practical trading",
                "Compare Kelly to other sizing methods"
            ),
            topics = listOf(
                "Kelly Criterion derivation and formula",
                "Full Kelly vs fractional Kelly (0.25 optimal)",
                "Kelly for multiple simultaneous bets",
                "Kelly limitations and practical adjustments"
            ),
            practicalExercises = listOf(
                "Calculate Kelly fraction for 20 trades",
                "Compare full vs 0.25 Kelly performance",
                "Build Kelly-based position sizing system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 38,
            moduleId = 4,
            title = "Value at Risk (VaR) and Expected Shortfall",
            description = "Quantifying portfolio risk using statistical methods",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(37),
            objectives = listOf(
                "Calculate VaR using multiple methods",
                "Understand VaR limitations",
                "Use Expected Shortfall for tail risk"
            ),
            topics = listOf(
                "Historical VaR calculation",
                "Parametric VaR",
                "Monte Carlo VaR",
                "Expected Shortfall (CVaR)"
            ),
            practicalExercises = listOf(
                "Calculate VaR using 3 methods",
                "Analyse VaR backtesting results",
                "Implement Expected Shortfall"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 39,
            moduleId = 4,
            title = "Correlation and Diversification",
            description = "Building diversified portfolios using correlation analysis",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(38),
            objectives = listOf(
                "Analyse asset correlations",
                "Build diversified portfolios",
                "Understand correlation breakdown in crises"
            ),
            topics = listOf(
                "Correlation calculation and interpretation",
                "Correlation matrices",
                "Diversification benefits",
                "Correlation regime changes"
            ),
            practicalExercises = listOf(
                "Build correlation matrix for 20 assets",
                "Identify diversification opportunities",
                "Analyse correlation during market stress"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 40,
            moduleId = 4,
            title = "Portfolio Optimisation",
            description = "Modern portfolio theory and optimisation techniques",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(39),
            objectives = listOf(
                "Apply Modern Portfolio Theory",
                "Build efficient frontier",
                "Optimise portfolio weights"
            ),
            topics = listOf(
                "Mean-variance optimisation",
                "Efficient frontier construction",
                "Sharpe ratio maximisation",
                "Black-Litterman model"
            ),
            practicalExercises = listOf(
                "Build efficient frontier",
                "Optimise portfolio weights",
                "Apply Black-Litterman views"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 41,
            moduleId = 4,
            title = "Drawdown Management",
            description = "Strategies for managing and recovering from drawdowns",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(37, 38),
            objectives = listOf(
                "Understand drawdown psychology",
                "Implement drawdown limits",
                "Develop recovery strategies"
            ),
            topics = listOf(
                "Maximum drawdown analysis",
                "Drawdown duration and recovery",
                "Position sizing during drawdowns",
                "Circuit breakers and trading pauses"
            ),
            practicalExercises = listOf(
                "Analyse historical drawdowns",
                "Design drawdown management rules",
                "Create recovery playbook"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 42,
            moduleId = 4,
            title = "Tail Risk and Black Swan Events",
            description = "Preparing for and managing extreme market events",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(38, 41),
            objectives = listOf(
                "Understand fat-tailed distributions",
                "Implement tail risk hedging",
                "Develop black swan protocols"
            ),
            topics = listOf(
                "Fat tails and non-normal distributions",
                "Tail risk hedging strategies",
                "Options for tail protection",
                "Black swan event protocols"
            ),
            practicalExercises = listOf(
                "Analyse historical tail events",
                "Design tail risk hedging strategy",
                "Create black swan response plan"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 43,
            moduleId = 4,
            title = "Leverage and Margin Management",
            description = "Safe use of leverage and margin in trading",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(37, 41),
            objectives = listOf(
                "Understand leverage mechanics",
                "Calculate margin requirements",
                "Manage leverage risk effectively"
            ),
            topics = listOf(
                "Leverage calculation and impact",
                "Margin requirements and maintenance",
                "Margin call prevention",
                "Optimal leverage levels"
            ),
            practicalExercises = listOf(
                "Calculate leverage scenarios",
                "Design margin management rules",
                "Build leverage monitoring system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 44,
            moduleId = 4,
            title = "Hedging Strategies",
            description = "Protecting portfolios using hedging techniques",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(39, 40),
            objectives = listOf(
                "Understand hedging principles",
                "Implement various hedging strategies",
                "Calculate hedge ratios"
            ),
            topics = listOf(
                "Direct and cross hedging",
                "Delta hedging",
                "Portfolio hedging with futures",
                "Options hedging strategies"
            ),
            practicalExercises = listOf(
                "Design hedging strategies",
                "Calculate optimal hedge ratios",
                "Implement portfolio hedge"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 45,
            moduleId = 4,
            title = "Liquidity Risk Management",
            description = "Managing liquidity risk in trading and portfolios",
            durationMinutes = 120,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(4, 38),
            objectives = listOf(
                "Understand liquidity risk",
                "Measure and monitor liquidity",
                "Manage positions in illiquid markets"
            ),
            topics = listOf(
                "Liquidity metrics and measurement",
                "Market impact modelling",
                "Liquidity-adjusted VaR",
                "Illiquid position management"
            ),
            practicalExercises = listOf(
                "Analyse liquidity across assets",
                "Calculate market impact",
                "Design liquidity management rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 46,
            moduleId = 4,
            title = "Counterparty and Operational Risk",
            description = "Managing non-market risks in trading",
            durationMinutes = 120,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(38),
            objectives = listOf(
                "Identify counterparty risks",
                "Implement operational risk controls",
                "Develop risk mitigation strategies"
            ),
            topics = listOf(
                "Counterparty risk assessment",
                "Exchange and broker risk",
                "Operational risk categories",
                "Business continuity planning"
            ),
            practicalExercises = listOf(
                "Assess counterparty risks",
                "Design operational controls",
                "Create business continuity plan"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 75
        ),
        Lesson(
            id = 47,
            moduleId = 4,
            title = "Risk Reporting and Monitoring",
            description = "Building comprehensive risk monitoring systems",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(38, 39, 40, 41),
            objectives = listOf(
                "Design risk dashboards",
                "Implement real-time monitoring",
                "Create risk reports"
            ),
            topics = listOf(
                "Key risk indicators",
                "Real-time risk monitoring",
                "Risk reporting frameworks",
                "Escalation procedures"
            ),
            practicalExercises = listOf(
                "Design risk dashboard",
                "Build risk monitoring system",
                "Create risk report template"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 48,
            moduleId = 4,
            title = "Risk Management Module Assessment",
            description = "Comprehensive assessment of risk management mastery",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47),
            objectives = listOf(
                "Demonstrate mastery of risk management",
                "Apply risk techniques to real portfolios",
                "Earn Risk Manager Certificate"
            ),
            topics = listOf(
                "Comprehensive risk management review",
                "Portfolio risk analysis",
                "Crisis scenario analysis"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Perform full portfolio risk analysis",
                "Present risk management framework to assessors"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 85
        )
    )

    val MODULE_4_RISK = Module(
        id = 4,
        name = "Advanced Risk Management",
        description = "Professional-grade risk management and portfolio construction",
        lessons = module4Lessons,
        certification = "Risk Manager Certificate",
        estimatedHours = 26
    )

    // ========================================================================
    // MODULE 5: ADVANCED TRADING STRATEGIES (Lessons 49-60)
    // ========================================================================
    
    private val module5Lessons = listOf(
        Lesson(
            id = 49,
            moduleId = 5,
            title = "Momentum Trading Strategies",
            description = "Capturing trends using momentum-based approaches",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(17, 19, 24),
            objectives = listOf(
                "Build momentum trading systems",
                "Manage momentum strategy risk",
                "Optimise momentum parameters"
            ),
            topics = listOf(
                "Time-series momentum",
                "Cross-sectional momentum",
                "Momentum crashes and protection",
                "Momentum factor combinations"
            ),
            practicalExercises = listOf(
                "Build momentum trading system",
                "Backtest momentum strategies",
                "Implement crash protection"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 50,
            moduleId = 5,
            title = "Mean Reversion Strategies",
            description = "Profiting from price reversals to the mean",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(17, 18, 24),
            objectives = listOf(
                "Identify mean reversion opportunities",
                "Build mean reversion systems",
                "Manage mean reversion risk"
            ),
            topics = listOf(
                "Statistical mean reversion",
                "Pairs trading",
                "Bollinger Band mean reversion",
                "Mean reversion vs trend following"
            ),
            practicalExercises = listOf(
                "Build mean reversion system",
                "Identify pairs trading opportunities",
                "Backtest mean reversion strategies"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 51,
            moduleId = 5,
            title = "Breakout Trading Strategies",
            description = "Trading price breakouts from consolidation",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(14, 18, 24),
            objectives = listOf(
                "Identify high-probability breakouts",
                "Filter false breakouts",
                "Manage breakout trade risk"
            ),
            topics = listOf(
                "Breakout identification methods",
                "Volume confirmation",
                "False breakout filtering",
                "Breakout trade management"
            ),
            practicalExercises = listOf(
                "Identify 20 breakout setups",
                "Build breakout trading system",
                "Analyse breakout success rates"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 52,
            moduleId = 5,
            title = "Scalping and High-Frequency Concepts",
            description = "Understanding short-term trading approaches",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(4, 24),
            objectives = listOf(
                "Understand scalping mechanics",
                "Learn HFT concepts",
                "Evaluate scalping viability"
            ),
            topics = listOf(
                "Scalping strategies and execution",
                "Order flow scalping",
                "HFT overview and impact",
                "Scalping infrastructure requirements"
            ),
            practicalExercises = listOf(
                "Paper trade scalping strategies",
                "Analyse scalping profitability",
                "Evaluate infrastructure needs"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 53,
            moduleId = 5,
            title = "Swing Trading Mastery",
            description = "Capturing multi-day to multi-week price swings",
            durationMinutes = 180,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(6, 14, 15, 24),
            objectives = listOf(
                "Master swing trading methodology",
                "Identify optimal swing setups",
                "Manage swing trade risk"
            ),
            topics = listOf(
                "Swing trading timeframes",
                "Swing setup identification",
                "Swing trade management",
                "Swing trading psychology"
            ),
            practicalExercises = listOf(
                "Identify 20 swing setups",
                "Execute swing trading plan",
                "Analyse swing trade results"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 54,
            moduleId = 5,
            title = "Position Trading and Investing",
            description = "Long-term position trading strategies",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(36, 48),
            objectives = listOf(
                "Develop position trading approach",
                "Combine technical and fundamental",
                "Manage long-term positions"
            ),
            topics = listOf(
                "Position trading methodology",
                "Long-term trend identification",
                "Position sizing for long-term",
                "Tax-efficient position management"
            ),
            practicalExercises = listOf(
                "Build position trading portfolio",
                "Develop entry/exit criteria",
                "Create position management rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 55,
            moduleId = 5,
            title = "Options Strategies for Traders",
            description = "Using options to enhance trading strategies",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(48),
            objectives = listOf(
                "Understand options basics",
                "Implement options strategies",
                "Use options for hedging and income"
            ),
            topics = listOf(
                "Options fundamentals",
                "Covered calls and protective puts",
                "Spreads: vertical, horizontal, diagonal",
                "Options Greeks and risk management"
            ),
            practicalExercises = listOf(
                "Execute options strategies",
                "Calculate options Greeks",
                "Build options trading plan"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 56,
            moduleId = 5,
            title = "Arbitrage Strategies",
            description = "Identifying and exploiting price inefficiencies",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(4, 48),
            objectives = listOf(
                "Understand arbitrage types",
                "Identify arbitrage opportunities",
                "Execute arbitrage trades"
            ),
            topics = listOf(
                "Pure arbitrage vs statistical arbitrage",
                "Crypto arbitrage opportunities",
                "Cross-exchange arbitrage",
                "Arbitrage execution challenges"
            ),
            practicalExercises = listOf(
                "Identify arbitrage opportunities",
                "Calculate arbitrage profits",
                "Build arbitrage monitoring system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 57,
            moduleId = 5,
            title = "Market Making Concepts",
            description = "Understanding market making strategies",
            durationMinutes = 150,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(4, 52),
            objectives = listOf(
                "Understand market making mechanics",
                "Learn spread management",
                "Evaluate market making viability"
            ),
            topics = listOf(
                "Market making fundamentals",
                "Spread calculation and management",
                "Inventory risk management",
                "Market making in crypto"
            ),
            practicalExercises = listOf(
                "Simulate market making",
                "Calculate optimal spreads",
                "Analyse market making profitability"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 58,
            moduleId = 5,
            title = "Algorithmic Trading Introduction",
            description = "Building automated trading systems",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(48, 49, 50),
            objectives = listOf(
                "Understand algo trading components",
                "Design trading algorithms",
                "Implement basic algo systems"
            ),
            topics = listOf(
                "Algo trading architecture",
                "Signal generation",
                "Execution algorithms",
                "Algo risk management"
            ),
            practicalExercises = listOf(
                "Design trading algorithm",
                "Implement signal generation",
                "Build basic algo system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 59,
            moduleId = 5,
            title = "Strategy Combination and Portfolio",
            description = "Combining multiple strategies for robust performance",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(49, 50, 51, 53, 54),
            objectives = listOf(
                "Combine uncorrelated strategies",
                "Build strategy portfolios",
                "Manage multi-strategy risk"
            ),
            topics = listOf(
                "Strategy correlation analysis",
                "Strategy allocation methods",
                "Multi-strategy portfolio construction",
                "Strategy rotation"
            ),
            practicalExercises = listOf(
                "Analyse strategy correlations",
                "Build multi-strategy portfolio",
                "Implement strategy rotation"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 60,
            moduleId = 5,
            title = "Advanced Strategies Module Assessment",
            description = "Comprehensive assessment of advanced strategy mastery",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59),
            objectives = listOf(
                "Demonstrate mastery of advanced strategies",
                "Build comprehensive trading system",
                "Earn Advanced Strategist Certificate"
            ),
            topics = listOf(
                "Comprehensive strategy review",
                "Multi-strategy system design",
                "Live strategy presentation"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Present multi-strategy system",
                "Defend strategy choices to assessors"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 85
        )
    )

    val MODULE_5_STRATEGIES = Module(
        id = 5,
        name = "Advanced Trading Strategies",
        description = "Sophisticated trading strategies used by professional traders",
        lessons = module5Lessons,
        certification = "Advanced Strategist Certificate",
        estimatedHours = 30
    )

    // ========================================================================
    // MODULE 6: INSTITUTIONAL TRADING METHODS (Lessons 61-72)
    // ========================================================================
    
    private val module6Lessons = listOf(
        Lesson(
            id = 61,
            moduleId = 6,
            title = "Institutional Order Flow Analysis",
            description = "Reading and interpreting institutional activity",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(22, 23, 60),
            objectives = listOf(
                "Identify institutional order flow",
                "Interpret large order activity",
                "Trade alongside institutions"
            ),
            topics = listOf(
                "Institutional footprints in markets",
                "Block trade analysis",
                "Dark pool activity interpretation",
                "COT report analysis"
            ),
            practicalExercises = listOf(
                "Analyse institutional order flow",
                "Identify block trade patterns",
                "Interpret COT data"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 62,
            moduleId = 6,
            title = "Smart Money Concepts",
            description = "Understanding how institutional traders operate",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(61),
            objectives = listOf(
                "Understand smart money behaviour",
                "Identify liquidity zones",
                "Trade with smart money flow"
            ),
            topics = listOf(
                "Order blocks and breaker blocks",
                "Fair value gaps",
                "Liquidity pools and stop hunts",
                "Institutional candle analysis"
            ),
            practicalExercises = listOf(
                "Identify order blocks on charts",
                "Map liquidity zones",
                "Build smart money trading plan"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 63,
            moduleId = 6,
            title = "Wyckoff Method",
            description = "Classic institutional trading methodology",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(22, 61),
            objectives = listOf(
                "Master Wyckoff market phases",
                "Identify accumulation and distribution",
                "Apply Wyckoff to modern markets"
            ),
            topics = listOf(
                "Wyckoff market cycle",
                "Accumulation schematics",
                "Distribution schematics",
                "Wyckoff trading rules"
            ),
            practicalExercises = listOf(
                "Identify Wyckoff phases on charts",
                "Analyse accumulation/distribution",
                "Build Wyckoff trading system"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 64,
            moduleId = 6,
            title = "Intermarket Analysis",
            description = "Trading using relationships between markets",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(25, 60),
            objectives = listOf(
                "Understand intermarket relationships",
                "Use leading indicators from other markets",
                "Build intermarket trading strategies"
            ),
            topics = listOf(
                "Stock-bond relationships",
                "Currency-commodity relationships",
                "Risk-on/risk-off dynamics",
                "Intermarket divergences"
            ),
            practicalExercises = listOf(
                "Analyse intermarket relationships",
                "Identify leading indicators",
                "Build intermarket strategy"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 65,
            moduleId = 6,
            title = "Quantitative Strategy Development",
            description = "Building and testing quantitative trading strategies",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(34, 58, 60),
            objectives = listOf(
                "Develop quantitative strategies",
                "Implement robust backtesting",
                "Avoid overfitting"
            ),
            topics = listOf(
                "Quantitative strategy design",
                "Backtesting methodology",
                "Walk-forward analysis",
                "Overfitting prevention"
            ),
            practicalExercises = listOf(
                "Design quantitative strategy",
                "Implement walk-forward testing",
                "Analyse strategy robustness"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 85
        ),
        Lesson(
            id = 66,
            moduleId = 6,
            title = "Machine Learning in Trading",
            description = "Applying ML techniques to trading strategies",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(65),
            objectives = listOf(
                "Understand ML applications in trading",
                "Build ML-based signals",
                "Avoid ML pitfalls in finance"
            ),
            topics = listOf(
                "ML for trading overview",
                "Feature engineering for markets",
                "Model selection and validation",
                "ML pitfalls and best practices"
            ),
            practicalExercises = listOf(
                "Build ML trading signal",
                "Validate ML model properly",
                "Compare ML to traditional methods"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 67,
            moduleId = 6,
            title = "Execution Optimisation",
            description = "Minimising execution costs and market impact",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(2, 4, 61),
            objectives = listOf(
                "Minimise execution costs",
                "Reduce market impact",
                "Optimise order execution"
            ),
            topics = listOf(
                "Transaction cost analysis",
                "Execution algorithms: TWAP, VWAP, IS",
                "Market impact models",
                "Optimal execution theory"
            ),
            practicalExercises = listOf(
                "Analyse transaction costs",
                "Implement execution algorithms",
                "Optimise order execution"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 68,
            moduleId = 6,
            title = "Portfolio Construction for Institutions",
            description = "Building institutional-grade portfolios",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(40, 48, 59),
            objectives = listOf(
                "Build institutional portfolios",
                "Implement factor-based allocation",
                "Manage portfolio constraints"
            ),
            topics = listOf(
                "Institutional portfolio construction",
                "Factor-based allocation",
                "Constraint optimisation",
                "Rebalancing strategies"
            ),
            practicalExercises = listOf(
                "Build institutional portfolio",
                "Implement factor allocation",
                "Design rebalancing rules"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 69,
            moduleId = 6,
            title = "Performance Attribution",
            description = "Analysing sources of portfolio returns",
            durationMinutes = 150,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(68),
            objectives = listOf(
                "Perform return attribution",
                "Identify alpha sources",
                "Improve strategy performance"
            ),
            topics = listOf(
                "Return attribution methods",
                "Factor attribution",
                "Alpha and beta decomposition",
                "Performance improvement"
            ),
            practicalExercises = listOf(
                "Perform return attribution",
                "Identify alpha sources",
                "Develop improvement plan"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 80
        ),
        Lesson(
            id = 70,
            moduleId = 6,
            title = "Regulatory Compliance and Ethics",
            description = "Understanding trading regulations and ethical standards",
            durationMinutes = 120,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(60),
            objectives = listOf(
                "Understand trading regulations",
                "Implement compliance procedures",
                "Maintain ethical standards"
            ),
            topics = listOf(
                "Securities regulations overview",
                "Market manipulation rules",
                "Insider trading regulations",
                "Ethical trading practices"
            ),
            practicalExercises = listOf(
                "Review compliance requirements",
                "Design compliance procedures",
                "Analyse ethical scenarios"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 85
        ),
        Lesson(
            id = 71,
            moduleId = 6,
            title = "Trading Business Management",
            description = "Running trading as a professional business",
            durationMinutes = 150,
            difficulty = Difficulty.ADVANCED,
            prerequisites = listOf(60),
            objectives = listOf(
                "Structure trading business",
                "Manage trading capital",
                "Plan for long-term success"
            ),
            topics = listOf(
                "Trading business structures",
                "Capital management",
                "Tax considerations",
                "Business planning"
            ),
            practicalExercises = listOf(
                "Create trading business plan",
                "Design capital structure",
                "Develop long-term roadmap"
            ),
            assessmentType = AssessmentType.PRACTICAL,
            passingScore = 75
        ),
        Lesson(
            id = 72,
            moduleId = 6,
            title = "Institutional Methods Module Assessment",
            description = "Comprehensive assessment of institutional trading mastery",
            durationMinutes = 300,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71),
            objectives = listOf(
                "Demonstrate mastery of institutional methods",
                "Present institutional-grade strategy",
                "Earn Institutional Trader Certificate"
            ),
            topics = listOf(
                "Comprehensive institutional methods review",
                "Full strategy presentation",
                "Professional assessment"
            ),
            practicalExercises = listOf(
                "Complete comprehensive written exam",
                "Present institutional strategy",
                "Defend methodology to panel"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 85
        )
    )

    val MODULE_6_INSTITUTIONAL = Module(
        id = 6,
        name = "Institutional Trading Methods",
        description = "Professional techniques used by hedge funds and institutions",
        lessons = module6Lessons,
        certification = "Institutional Trader Certificate",
        estimatedHours = 32
    )

    // ========================================================================
    // MODULE 7: MASTERY AND CERTIFICATION (Lessons 73-76)
    // ========================================================================
    
    private val module7Lessons = listOf(
        Lesson(
            id = 73,
            moduleId = 7,
            title = "Comprehensive Knowledge Review",
            description = "Review of all programme content",
            durationMinutes = 240,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(12, 24, 36, 48, 60, 72),
            objectives = listOf(
                "Review all programme content",
                "Identify knowledge gaps",
                "Prepare for final assessment"
            ),
            topics = listOf(
                "Foundation review",
                "Technical analysis review",
                "Fundamental analysis review",
                "Risk management review",
                "Advanced strategies review",
                "Institutional methods review"
            ),
            practicalExercises = listOf(
                "Complete comprehensive review quiz",
                "Identify and address knowledge gaps",
                "Create study plan for final exam"
            ),
            assessmentType = AssessmentType.QUIZ,
            passingScore = 80
        ),
        Lesson(
            id = 74,
            moduleId = 7,
            title = "Live Trading Simulation",
            description = "Extended live trading simulation assessment",
            durationMinutes = 480,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(73),
            objectives = listOf(
                "Demonstrate trading skills in simulation",
                "Apply all learned concepts",
                "Achieve consistent profitability"
            ),
            topics = listOf(
                "Live market analysis",
                "Real-time trade execution",
                "Risk management in action",
                "Performance under pressure"
            ),
            practicalExercises = listOf(
                "Complete 2-week trading simulation",
                "Maintain trading journal",
                "Achieve performance targets"
            ),
            assessmentType = AssessmentType.SIMULATION,
            passingScore = 75
        ),
        Lesson(
            id = 75,
            moduleId = 7,
            title = "Final Written Examination",
            description = "Comprehensive written examination",
            durationMinutes = 300,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(73),
            objectives = listOf(
                "Demonstrate comprehensive knowledge",
                "Apply concepts to scenarios",
                "Pass final written exam"
            ),
            topics = listOf(
                "All programme topics",
                "Scenario-based questions",
                "Case study analysis"
            ),
            practicalExercises = listOf(
                "Complete 200-question exam",
                "Analyse 5 case studies",
                "Write strategy essay"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 80
        ),
        Lesson(
            id = 76,
            moduleId = 7,
            title = "Master Trader Certification",
            description = "Final certification and graduation",
            durationMinutes = 180,
            difficulty = Difficulty.EXPERT,
            prerequisites = listOf(74, 75),
            objectives = listOf(
                "Complete final presentation",
                "Receive Master Trader Certificate",
                "Join Sovereign Vantage Alumni Network"
            ),
            topics = listOf(
                "Final strategy presentation",
                "Panel Q&A",
                "Certification ceremony"
            ),
            practicalExercises = listOf(
                "Present complete trading system",
                "Defend strategy to expert panel",
                "Receive certification"
            ),
            assessmentType = AssessmentType.CERTIFICATION,
            passingScore = 85
        )
    )

    val MODULE_7_MASTERY = Module(
        id = 7,
        name = "Mastery and Certification",
        description = "Final mastery assessment and professional certification",
        lessons = module7Lessons,
        certification = "Sovereign Vantage Master Trader Certificate",
        estimatedHours = 20
    )
}
