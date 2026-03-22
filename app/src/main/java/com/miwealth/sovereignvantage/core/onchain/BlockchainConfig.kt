package com.miwealth.sovereignvantage.core.onchain

/**
 * Blockchain Configuration
 * Defines connection parameters for 50+ supported chains.
 */
object BlockchainConfig {

    data class ChainConfig(
        val name: String,
        val symbol: String,
        val chainId: Int,
        val rpcUrl: String,
        val explorerUrl: String,
        val type: ChainType
    )

    enum class ChainType { EVM, UTXO, SOLANA, COSMOS, OTHER }

    val SUPPORTED_CHAINS = listOf(
        // Layer 1 - Majors
        ChainConfig("Bitcoin", "BTC", 0, "https://btc.aegis-node.net", "https://mempool.space", ChainType.UTXO),
        ChainConfig("Ethereum", "ETH", 1, "https://eth.aegis-node.net", "https://etherscan.io", ChainType.EVM),
        ChainConfig("Binance Smart Chain", "BNB", 56, "https://bsc-dataseed.binance.org", "https://bscscan.com", ChainType.EVM),
        ChainConfig("Solana", "SOL", 101, "https://api.mainnet-beta.solana.com", "https://solscan.io", ChainType.SOLANA),
        ChainConfig("Cardano", "ADA", 0, "https://cardano-mainnet.blockfrost.io", "https://cardanoscan.io", ChainType.OTHER),
        ChainConfig("Polkadot", "DOT", 0, "wss://rpc.polkadot.io", "https://polkadot.subscan.io", ChainType.OTHER),
        ChainConfig("Avalanche", "AVAX", 43114, "https://api.avax.network/ext/bc/C/rpc", "https://snowtrace.io", ChainType.EVM),
        
        // Layer 2s
        ChainConfig("Polygon", "MATIC", 137, "https://polygon-rpc.com", "https://polygonscan.com", ChainType.EVM),
        ChainConfig("Arbitrum One", "ARB", 42161, "https://arb1.arbitrum.io/rpc", "https://arbiscan.io", ChainType.EVM),
        ChainConfig("Optimism", "OP", 10, "https://mainnet.optimism.io", "https://optimistic.etherscan.io", ChainType.EVM),
        
        // Others
        ChainConfig("Fantom", "FTM", 250, "https://rpc.ftm.tools", "https://ftmscan.com", ChainType.EVM),
        ChainConfig("Cronos", "CRO", 25, "https://evm.cronos.org", "https://cronoscan.com", ChainType.EVM),
        ChainConfig("Near", "NEAR", 0, "https://rpc.mainnet.near.org", "https://explorer.near.org", ChainType.OTHER),
        ChainConfig("Tron", "TRX", 0, "https://api.trongrid.io", "https://tronscan.org", ChainType.OTHER),
        ChainConfig("Cosmos Hub", "ATOM", 0, "https://rpc.cosmos.network", "https://www.mintscan.io/cosmos", ChainType.COSMOS)
    )

    fun getChainBySymbol(symbol: String): ChainConfig? {
        return SUPPORTED_CHAINS.find { it.symbol == symbol }
    }
}
