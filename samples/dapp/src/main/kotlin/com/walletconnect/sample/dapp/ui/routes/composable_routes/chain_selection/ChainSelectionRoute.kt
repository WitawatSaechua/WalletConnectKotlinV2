package com.walletconnect.sample.dapp.ui.routes.composable_routes.chain_selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.walletconnect.sample_common.Chains
import com.walletconnect.sample_common.CompletePreviews
import com.walletconnect.sample_common.ui.*
import com.walletconnect.sample_common.ui.commons.BlueButton
import com.walletconnect.sample_common.ui.theme.PreviewTheme

@Composable
fun ChainSelectionRoute(navController: NavController) {
    val viewModel: ChainSelectionViewModel = viewModel()
    val chainsState by viewModel.uiState.collectAsState()

    ChainSelectionScreen(
        chains = chainsState,
        onChainClick = viewModel::updateChainSelectState,
        onConnectClick = {
            //todo navigate to new paring or select paring route
        }
    )
}

@Composable
private fun ChainSelectionScreen(
    chains: List<ChainSelectionUi>,
    onChainClick: (Int, Boolean) -> Unit,
    onConnectClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WCTopAppBar(titleText = "Chain selection")
        ChainsList(
            chains = chains,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            onChainClick,
        )
        BlueButton(
            text = "Connect",
            onClick = onConnectClick,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ChainsList(
    chains: List<ChainSelectionUi>,
    modifier: Modifier,
    onChainClick: (Int, Boolean) -> Unit
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(chains) { index, chain ->
            ChainItem(
                index = index,
                chain = chain,
                onChainClick = onChainClick
            )
        }
    }
}

@Composable
private fun ChainItem(
    index: Int,
    chain: ChainSelectionUi,
    onChainClick: (Int, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clickable {
                onChainClick(index, chain.isSelected)
            }
            .conditionalModifier(chain.isSelected) {
                Modifier.coloredShadow(
                    chain.color.toColor(),
                    borderRadius = 8.dp,
                    blurRadius = 8.dp,
                    spread = 2f
                )
            }
            .border(width = 1.dp, color = chain.color.toColor(), shape = RoundedCornerShape(8.dp))
            .background(color = MaterialTheme.colors.background, shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = ImageVector.vectorResource(id = chain.icon),
            contentDescription = "${chain.chainName} icon"
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = chain.chainName)
    }
}

@CompletePreviews
@Composable
private fun ChainSelectionScreenPreview(
    @PreviewParameter(ChainSelectionStateProvider::class) chains: List<ChainSelectionUi>
) {
    PreviewTheme {
        ChainSelectionScreen(
            chains = chains,
            onChainClick = { _, _ -> },
            onConnectClick = {}
        )
    }
}

private class ChainSelectionStateProvider : PreviewParameterProvider<List<ChainSelectionUi>> {
    override val values: Sequence<List<ChainSelectionUi>>
        get() = sequenceOf(
            Chains.values().map { it.toChainUiState() }
        )
}