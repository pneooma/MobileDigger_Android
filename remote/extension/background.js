// Global shortcuts → the open MobileDigger Remote tab, which holds the Bluetooth connection.
const PAGE = 'remote.html';

async function openPage() {
  await chrome.tabs.create({ url: chrome.runtime.getURL(PAGE) });
}

chrome.commands.onCommand.addListener(async (cmd) => {
  try {
    await chrome.runtime.sendMessage({ type: 'cmd', cmd });
  } catch (_) {
    // No remote tab open: open it so the user can connect.
    await openPage();
  }
});

chrome.action.onClicked.addListener(openPage);
chrome.runtime.onInstalled.addListener(({ reason }) => { if (reason === 'install') openPage(); });
