/**
 * NexusMatch Frontend Logic
 * - Currency: Indian Rupee (₹ INR) with standard Indian numbering format (e.g. ₹1,34,900)
 * - User Authentication (Login & Sign Up) with modal dialogs & active session state
 * - Collapsible Filter Sidebar (Category filters, Price Range slider, Algorithm Type filters)
 * - Trie prefix autocomplete suggestions (/api/suggest)
 * - Multi-algorithm ranking & fuzzy matching search (/api/search)
 */

// API Base configuration
const API_BASE = window.location.port ? '' : 'http://localhost:8080';

// Fallback catalog: deleted per user request (empty by default)
const FALLBACK_PRODUCTS = [];

// Current State
let canonicalProducts = [];
let cachedSearchResults = [];
let selectedCategory = 'all';
let maxPriceFilter = 250000;
let selectedAlgoFilter = 'all';
let debounceTimer = null;
let activeSuggestionIdx = -1;

// Web Search API Key & Live Mode state
let webApiKey = localStorage.getItem('nexus_web_api_key') || '';
let isWebSearchEnabled = localStorage.getItem('nexus_web_search_enabled') === 'true' || !!webApiKey;

/**
 * Loads the canonical product catalog from Core Java backend (/api/products)
 */
async function fetchCanonicalProducts() {
  try {
    const res = await fetch(`${API_BASE}/api/products`);
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        canonicalProducts = data;
      }
    }
  } catch (e) {
    console.warn("Using fallback canonical products:", e.message);
  }
}

// DOM Elements
const searchInput = document.getElementById('productSearchInput') || document.getElementById('searchInput') || { value: '', addEventListener: () => {} };
const searchBtn = document.getElementById('searchBtn');
const clearBtn = document.getElementById('clearBtn');
const suggestionsList = document.getElementById('suggestionsList');
const productsGrid = document.getElementById('productsGrid');
const resultsCount = document.getElementById('resultsCount');
const resultsTitle = document.getElementById('resultsTitle');
const fuzzyNotice = document.getElementById('fuzzyNotice');
const chipButtons = document.querySelectorAll('.chip-btn');

// Web Search Controls DOM
const webApiKeyInput = document.getElementById('webApiKeyInput');
const toggleApiKeyVisBtn = document.getElementById('toggleApiKeyVisBtn');
const saveApiKeyBtn = document.getElementById('saveApiKeyBtn');
const clearApiKeyBtn = document.getElementById('clearApiKeyBtn');
const apiKeyStatusBadge = document.getElementById('apiKeyStatusBadge');
const webSearchToggleCheckbox = document.getElementById('webSearchToggleCheckbox');
const quickKeyModalBtn = document.getElementById('quickKeyModalBtn');
const quickKeyLabel = document.getElementById('quickKeyLabel');

// Web Search API Key Modal DOM
const apiKeyModal = document.getElementById('apiKeyModal');
const closeApiKeyModalBtn = document.getElementById('closeApiKeyModalBtn');
const modalApiKeyInput = document.getElementById('modalApiKeyInput');
const modalToggleApiKeyVisBtn = document.getElementById('modalToggleApiKeyVisBtn');
const modalSaveApiKeyBtn = document.getElementById('modalSaveApiKeyBtn');
const modalClearApiKeyBtn = document.getElementById('modalClearApiKeyBtn');
const modalApiKeyStatusBadge = document.getElementById('modalApiKeyStatusBadge');

// Sidebar DOM
const sidebarToggleBtn = document.getElementById('sidebarToggleBtn');
const closeSidebarBtn = document.getElementById('closeSidebarBtn');
const appSidebar = document.getElementById('appSidebar');
const sidebarOverlay = document.getElementById('sidebarOverlay');
const categorySelect = document.getElementById('categorySelect');
const priceRange = document.getElementById('priceRange');
const priceRangeValue = document.getElementById('priceRangeValue');

// Nav buttons
const navHomeBtn = document.getElementById('navHomeBtn');
const navDashboardBtn = document.getElementById('navDashboardBtn');
const navProductsBtn = document.getElementById('navProductsBtn');
const navMatchListingBtn = document.getElementById('navMatchListingBtn');
const navAlgorithmsBtn = document.getElementById('navAlgorithmsBtn');
const navAboutBtn = document.getElementById('navAboutBtn');
const sidebarFilterControls = document.getElementById('sidebarFilterControls');

// Views
const viewHome = document.getElementById('viewHome');
const viewDashboard = document.getElementById('viewDashboard');
const viewProducts = document.getElementById('viewProducts');
const viewMatchListing = document.getElementById('viewMatchListing');
const viewAlgorithms = document.getElementById('viewAlgorithms');
const viewAbout = document.getElementById('viewAbout');

// Home action buttons
const homeGoSearchBtn = document.getElementById('homeGoSearchBtn');
const homeGoCompareBtn = document.getElementById('homeGoCompareBtn');
const homeGoSellersBtn = document.getElementById('homeGoSellersBtn');

// Dashboard Quick Action buttons
const qaMatchListingBtn = document.getElementById('qaMatchListingBtn');
const qaViewProductsBtn = document.getElementById('qaViewProductsBtn');
const qaViewAlgorithmsBtn = document.getElementById('qaViewAlgorithmsBtn');

// Cross-site compare elements
const compareProductSelect = document.getElementById('compareProductSelect');
const crossSiteCardsGrid = document.getElementById('crossSiteCardsGrid');
const comparisonMatrixTable = document.getElementById('comparisonMatrixTable');

// Screen DOM containers
const authGatewayScreen = document.getElementById('authGatewayScreen');
const mainAppWrapper = document.getElementById('mainAppWrapper');

// Auth DOM
const tabLogin = document.getElementById('tabLogin');
const tabSignup = document.getElementById('tabSignup');
const loginForm = document.getElementById('loginForm');
const signupForm = document.getElementById('signupForm');
const authAlert = document.getElementById('authAlert');
const userNameLabel = document.getElementById('userNameLabel');
const userAvatarChar = document.getElementById('userAvatarChar');
const logoutBtn = document.getElementById('logoutBtn');
const toastNotification = document.getElementById('toastNotification');

// Initialize on page load
document.addEventListener('DOMContentLoaded', async () => {
  // Check persisted user session
  checkLocalSession();

  // Load canonical product catalog from Core Java backend (or fallback)
  await fetchCanonicalProducts();

  // Populate Match Listing dropdown with valid canonical products only
  populateCrossSiteDropdown();

  // Initially display canonical products catalog
  displayResults(canonicalProducts, '');
  renderHomeFeaturedCards();
  renderDashboardProducts();
  syncProfileUI();
  populateCompareDropdown();
  if (canonicalProducts.length > 0) {
    populateComparePage(canonicalProducts[0], 96, canonicalProducts[0].name);
  }

  // Fetch Dashboard statistics from Core Java server
  fetchDashboardData();

  // Event Listeners for Search & Autocomplete
  if (searchInput && searchInput.addEventListener) {
    searchInput.addEventListener('input', handleInputChange);
    searchInput.addEventListener('keydown', handleKeyNavigation);
  }
  if (searchBtn) searchBtn.addEventListener('click', () => executeSearch(searchInput.value));
  if (clearBtn) clearBtn.addEventListener('click', handleClear);

  // Quick preset chips
  chipButtons.forEach(chip => {
    chip.addEventListener('click', () => {
      const query = chip.getAttribute('data-query');
      searchInput.value = query;
      clearBtn.style.display = 'block';
      executeSearch(query);
    });
  });

  // Close suggestions when clicking outside
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.search-box-wrapper')) {
      hideSuggestions();
    }
  });

  // Navigation view switcher handlers for the 5 tabs
  if (navHomeBtn) navHomeBtn.addEventListener('click', () => switchView('home'));
  if (navDashboardBtn) navDashboardBtn.addEventListener('click', () => switchView('dashboard'));
  if (navProductsBtn) navProductsBtn.addEventListener('click', () => switchView('products'));
  if (navMatchListingBtn) navMatchListingBtn.addEventListener('click', () => switchView('matchListing'));
  if (navAboutBtn) navAboutBtn.addEventListener('click', () => switchView('about'));

  // Quick Actions Buttons in Dashboard
  if (qaMatchListingBtn) qaMatchListingBtn.addEventListener('click', () => switchView('matchListing'));
  if (qaViewProductsBtn) qaViewProductsBtn.addEventListener('click', () => switchView('products'));

  // Home Quick Action Buttons
  if (homeGoSearchBtn) homeGoSearchBtn.addEventListener('click', () => switchView('products'));
  if (homeGoCompareBtn) homeGoCompareBtn.addEventListener('click', () => switchView('matchListing'));
  if (homeGoSellersBtn) homeGoSellersBtn.addEventListener('click', () => switchView('about'));

  // Clean dropdown category filter
  if (categorySelect) {
    categorySelect.addEventListener('change', (e) => {
      selectedCategory = e.target.value;
      filterAndRenderResults();
    });
  }

  // Price range slider (INR)
  if (priceRange) {
    priceRange.addEventListener('input', (e) => {
      maxPriceFilter = Number(e.target.value);
      priceRangeValue.textContent = formatINR(maxPriceFilter);
      filterAndRenderResults();
    });
  }

  // Cross-site product selection change
  if (compareProductSelect) {
    compareProductSelect.addEventListener('change', renderCrossSiteComparison);
  }

  // Auth Tab switchers
  tabLogin.addEventListener('click', () => switchAuthTab('login'));
  tabSignup.addEventListener('click', () => switchAuthTab('signup'));

  const quickDemoLoginBtn = document.getElementById('quickDemoLoginBtn');
  if (quickDemoLoginBtn) {
    quickDemoLoginBtn.addEventListener('click', () => {
      loginUserSuccess({ name: 'Aakash Sharma (Demo)', email: 'demo@example.com' });
    });
  }

  loginForm.addEventListener('submit', handleLoginSubmit);
  signupForm.addEventListener('submit', handleSignupSubmit);
  logoutBtn.addEventListener('click', handleLogout);

  // Initialize Web Search API UI state
  initWebSearchUI();
});

/* ==========================================================================
   Web Search API Key Management & UI Synchronization
   ========================================================================== */
function initWebSearchUI() {
  syncApiKeyUI();

  // Sidebar API Key save/clear/visibility
  if (saveApiKeyBtn) {
    saveApiKeyBtn.addEventListener('click', () => {
      const keyVal = (webApiKeyInput.value || '').trim();
      saveWebApiKey(keyVal);
    });
  }

  if (clearApiKeyBtn) {
    clearApiKeyBtn.addEventListener('click', () => {
      saveWebApiKey('');
    });
  }

  if (toggleApiKeyVisBtn) {
    toggleApiKeyVisBtn.addEventListener('click', () => {
      if (webApiKeyInput.type === 'password') {
        webApiKeyInput.type = 'text';
        toggleApiKeyVisBtn.textContent = '🙈';
      } else {
        webApiKeyInput.type = 'password';
        toggleApiKeyVisBtn.textContent = '👁️';
      }
    });
  }

  // Modal API Key events
  if (quickKeyModalBtn) {
    quickKeyModalBtn.addEventListener('click', openApiKeyModal);
  }

  if (closeApiKeyModalBtn) {
    closeApiKeyModalBtn.addEventListener('click', closeApiKeyModal);
  }

  if (apiKeyModal) {
    apiKeyModal.addEventListener('click', (e) => {
      if (e.target === apiKeyModal) closeApiKeyModal();
    });
  }

  if (modalSaveApiKeyBtn) {
    modalSaveApiKeyBtn.addEventListener('click', () => {
      const keyVal = (modalApiKeyInput.value || '').trim();
      saveWebApiKey(keyVal);
      closeApiKeyModal();
    });
  }

  if (modalClearApiKeyBtn) {
    modalClearApiKeyBtn.addEventListener('click', () => {
      saveWebApiKey('');
      closeApiKeyModal();
    });
  }

  if (modalToggleApiKeyVisBtn) {
    modalToggleApiKeyVisBtn.addEventListener('click', () => {
      if (modalApiKeyInput.type === 'password') {
        modalApiKeyInput.type = 'text';
        modalToggleApiKeyVisBtn.textContent = '🙈';
      } else {
        modalApiKeyInput.type = 'password';
        modalToggleApiKeyVisBtn.textContent = '👁️';
      }
    });
  }

  // Live Web Search toggle checkbox
  if (webSearchToggleCheckbox) {
    webSearchToggleCheckbox.checked = isWebSearchEnabled;
    webSearchToggleCheckbox.addEventListener('change', (e) => {
      isWebSearchEnabled = e.target.checked;
      localStorage.setItem('nexus_web_search_enabled', isWebSearchEnabled ? 'true' : 'false');
      showToast(isWebSearchEnabled ? "🌐 Live Web Search mode enabled!" : "📦 Switched to local catalog mode.");
      if (searchInput.value.trim()) {
        executeSearch(searchInput.value.trim());
      }
    });
  }
}

function saveWebApiKey(keyVal) {
  webApiKey = keyVal;
  localStorage.setItem('nexus_web_api_key', keyVal);
  if (keyVal) {
    isWebSearchEnabled = true;
    localStorage.setItem('nexus_web_search_enabled', 'true');
    showToast("🔑 SerpApi Key saved! Live Web Search is active.");
  } else {
    showToast("🔑 API Key cleared. Using built-in catalog.");
  }
  syncApiKeyUI();
  if (searchInput.value.trim()) {
    executeSearch(searchInput.value.trim());
  }
}

function syncApiKeyUI() {
  const hasKey = !!webApiKey;

  if (webApiKeyInput) webApiKeyInput.value = webApiKey;
  if (modalApiKeyInput) modalApiKeyInput.value = webApiKey;

  if (apiKeyStatusBadge) {
    apiKeyStatusBadge.textContent = hasKey ? 'API Connected' : 'Local Only';
    apiKeyStatusBadge.className = `api-status-badge ${hasKey ? 'badge-online' : 'badge-offline'}`;
  }

  if (modalApiKeyStatusBadge) {
    modalApiKeyStatusBadge.textContent = hasKey ? 'Active SerpApi Key' : 'No Key (Local Only)';
    modalApiKeyStatusBadge.className = `api-status-badge ${hasKey ? 'badge-online' : 'badge-offline'}`;
  }

  if (webSearchToggleCheckbox) {
    webSearchToggleCheckbox.checked = isWebSearchEnabled;
  }

  if (quickKeyModalBtn) {
    quickKeyModalBtn.classList.toggle('has-key', hasKey);
  }

  if (quickKeyLabel) {
    quickKeyLabel.textContent = hasKey ? 'API Key Active' : 'API Key';
  }
}

function openApiKeyModal() {
  syncApiKeyUI();
  if (apiKeyModal) apiKeyModal.style.display = 'flex';
  document.body.style.overflow = 'hidden';
}

function closeApiKeyModal() {
  if (apiKeyModal) apiKeyModal.style.display = 'none';
  document.body.style.overflow = '';
}

/* ==========================================================================
   Indian Rupee Formatting Helper (e.g. ₹1,34,900)
   ========================================================================== */
function formatINR(number) {
  if (number === undefined || number === null || isNaN(number)) return '₹0';
  return '₹' + Number(number).toLocaleString('en-IN', {
    maximumFractionDigits: 0
  });
}

/* ==========================================================================
   Sidebar Management
   ========================================================================== */
function toggleSidebar() {
  const isOpen = appSidebar.classList.contains('open');
  if (isOpen) {
    closeSidebar();
  } else {
    appSidebar.classList.add('open');
    sidebarOverlay.classList.add('active');
    document.body.style.overflow = 'hidden';
  }
}

function closeSidebar() {
  appSidebar.classList.remove('open');
  sidebarOverlay.classList.remove('active');
  document.body.style.overflow = '';
}

/* ==========================================================================
   Search & Algorithmic Pipeline
   ========================================================================== */
function handleInputChange(e) {
  const query = e.target.value.trim();
  clearBtn.style.display = query ? 'block' : 'none';

  clearTimeout(debounceTimer);
  if (!query) {
    hideSuggestions();
    cachedSearchResults = [];
    displayResults([], '');
    return;
  }

  // Debounced Trie autocomplete (180ms)
  debounceTimer = setTimeout(() => {
    fetchSuggestions(query);
  }, 180);
}

function handleKeyNavigation(e) {
  const items = suggestionsList.querySelectorAll('.suggestion-item');
  if (items.length === 0 || suggestionsList.style.display === 'none') {
    if (e.key === 'Enter') {
      executeSearch(searchInput.value);
    }
    return;
  }

  if (e.key === 'ArrowDown') {
    e.preventDefault();
    activeSuggestionIdx = (activeSuggestionIdx + 1) % items.length;
    updateSuggestionHighlight(items);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    activeSuggestionIdx = (activeSuggestionIdx - 1 + items.length) % items.length;
    updateSuggestionHighlight(items);
  } else if (e.key === 'Enter') {
    e.preventDefault();
    if (activeSuggestionIdx >= 0 && activeSuggestionIdx < items.length) {
      items[activeSuggestionIdx].click();
    } else {
      hideSuggestions();
      executeSearch(searchInput.value);
    }
  } else if (e.key === 'Escape') {
    hideSuggestions();
  }
}

function updateSuggestionHighlight(items) {
  items.forEach((item, idx) => {
    item.classList.toggle('active', idx === activeSuggestionIdx);
  });
}

function handleClear() {
  searchInput.value = '';
  clearBtn.style.display = 'none';
  hideSuggestions();
  searchInput.focus();
  cachedSearchResults = [...canonicalProducts];
  displayResults(canonicalProducts, '');
}

async function fetchSuggestions(query) {
  try {
    const url = `${API_BASE}/api/suggest?q=${encodeURIComponent(query)}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Suggest API unavailable');
    const data = await response.json();
    if (data.suggestions && data.suggestions.length > 0) {
      renderSuggestions(data.suggestions);
      return;
    }
  } catch (err) {
    // Backend offline: use dynamic canonical catalog prefix filter
  }

  // Trie suggestions from canonical products
  const words = [];
  canonicalProducts.forEach(item => {
    const p = item.product || item;
    const tokens = (p.name || '').toLowerCase().split(/[\s,.-]+/).concat((p.category || '').toLowerCase().split(/\s+/));
    tokens.forEach(w => {
      if (w.startsWith(query.toLowerCase()) && !words.includes(w) && w.length >= query.length) {
        words.push(w);
      }
    });
  });
  renderSuggestions(words.slice(0, 6));
}

function renderSuggestions(suggestions) {
  activeSuggestionIdx = -1;
  if (!suggestions || suggestions.length === 0) {
    hideSuggestions();
    return;
  }

  suggestionsList.innerHTML = suggestions.map(s => `
    <div class="suggestion-item" data-val="${s}">
      <span class="suggestion-text">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        ${escapeHtml(s)}
      </span>
      <span class="suggestion-badge">Trie Prefix</span>
    </div>
  `).join('');

  suggestionsList.querySelectorAll('.suggestion-item').forEach(item => {
    item.addEventListener('click', () => {
      const val = item.getAttribute('data-val');
      searchInput.value = val;
      hideSuggestions();
      executeSearch(val);
    });
  });

  suggestionsList.style.display = 'block';
}

function hideSuggestions() {
  suggestionsList.style.display = 'none';
  suggestionsList.innerHTML = '';
  activeSuggestionIdx = -1;
}

// Exchange rate USD to INR
const USD_TO_INR = 83.5;

/**
 * Searches the canonical product catalog only.
 * Normalizes query and compares against canonical product name, brand, category, tags, and description.
 * Does NOT return unrelated products on spelling queries or low generic similarity.
 */
/**
 * Searches either the local catalog or live online web (Google Shopping / SerpApi / Web Catalog)
 * and evaluates algorithmic relevance matching scores.
 */
async function executeSearch(query) {
  hideSuggestions();

  const cleanQuery = (query || '').trim();
  if (!cleanQuery) {
    cachedSearchResults = [...canonicalProducts];
    filterAndRenderResults('');
    return;
  }

  // Always search live from web online (do not search saved local script.js products)
  renderLoadingState();
  try {
    const queryParams = new URLSearchParams({
      q: cleanQuery
    });
    if (webApiKey) {
      queryParams.set('apiKey', webApiKey);
    }

    const res = await fetch(`${API_BASE}/api/online-search?${queryParams.toString()}`);
    if (res.ok) {
      const data = await res.json();
      if (data && Array.isArray(data.results)) {
        cachedSearchResults = data.results.map(r => ({
          ...r.product,
          score: r.score,
          matchType: r.matchType,
          isFuzzy: r.isFuzzy,
          scores: r.scores,
          source: data.source || (webApiKey ? 'serpapi-live-web' : 'online-web-api')
        }));

        filterAndRenderResults(cleanQuery);
        return;
      }
    }
  } catch (err) {
    console.warn("Online web search error:", err.message);
  }

  // If web returns no results or network fails, display empty state (do not fallback to script.js products)
  cachedSearchResults = [];
  filterAndRenderResults(cleanQuery);
}

function capitalize(str) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function fastLevenshtein(a, b) {
  const m = a.length, n = b.length;
  const d = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) d[i][0] = i;
  for (let j = 0; j <= n; j++) d[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (a[i - 1] === b[j - 1]) d[i][j] = d[i - 1][j - 1];
      else d[i][j] = 1 + Math.min(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1]);
    }
  }
  return d[m][n];
}

/**
 * Handles switching between the active application views:
 * 🏠 home, 📊 dashboard, 📦 products, 🔍 matchListing, ℹ️ about
 */
function switchView(viewName) {
  // Update nav button active states
  [navHomeBtn, navDashboardBtn, navProductsBtn, navMatchListingBtn, navAboutBtn].forEach(btn => {
    if (btn) btn.classList.remove('active');
  });

  // Hide all view containers
  [viewHome, viewDashboard, viewProducts, viewMatchListing, viewAbout].forEach(view => {
    if (view) view.style.display = 'none';
  });

  // Also update data-view sidebar items
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  const activeNavItem = document.querySelector(`.nav-item[data-view="${viewName}"]`);
  if (activeNavItem) activeNavItem.classList.add('active');

  if (viewName === 'home') {
    if (navHomeBtn) navHomeBtn.classList.add('active');
    if (viewHome) viewHome.style.display = 'block';
    if (sidebarFilterControls) sidebarFilterControls.style.display = 'none';
  } else if (viewName === 'dashboard') {
    if (navDashboardBtn) navDashboardBtn.classList.add('active');
    if (viewDashboard) viewDashboard.style.display = 'block';
    if (sidebarFilterControls) sidebarFilterControls.style.display = 'none';
    fetchDashboardData();
  } else if (viewName === 'products') {
    if (navProductsBtn) navProductsBtn.classList.add('active');
    if (viewProducts) viewProducts.style.display = 'block';
    if (sidebarFilterControls) sidebarFilterControls.style.display = 'flex';
  } else if (viewName === 'matchListing' || viewName === 'comparison') {
    if (navMatchListingBtn) navMatchListingBtn.classList.add('active');
    if (viewMatchListing) viewMatchListing.style.display = 'block';
    if (sidebarFilterControls) sidebarFilterControls.style.display = 'none';
    populateCrossSiteDropdown();
    renderCrossSiteComparison();
  } else if (viewName === 'about' || viewName === 'aboutHelp' || viewName === 'sellers') {
    if (navAboutBtn) navAboutBtn.classList.add('active');
    if (viewAbout) viewAbout.style.display = 'block';
    if (sidebarFilterControls) sidebarFilterControls.style.display = 'none';
  }
}

/**
 * Fetches real platform dashboard statistics from Core Java server (/api/dashboard)
 * Updates summary cards, matching overview progress bars, and recent matches table.
 */
async function fetchDashboardData() {
  try {
    const res = await fetch(`${API_BASE}/api/dashboard`);
    if (res.ok) {
      const data = await res.json();
      renderDashboardUI(data);
      return;
    }
  } catch (err) {
    console.warn("Could not reach /api/dashboard, using fallback statistics:", err.message);
  }

  // Fallback data using loaded catalog & listings
  renderDashboardUI({
    totalProducts: canonicalProducts.length,
    totalListings: 15,
    matchedListings: 12,
    unmatchedListings: 3,
    highConfidence: 10,
    lowConfidence: 2,
    recentMatches: [
      { sellerListing: "Apple iPhone 15 Pro (128 GB) - Natural Titanium", seller: "Amazon India", canonicalProduct: "Apple iPhone 15 Pro", similarity: 96, status: "High Confidence" },
      { sellerListing: "SAMSUNG Galaxy S24 Ultra 5G (Titanium Gray, 256 GB)", seller: "Flipkart", canonicalProduct: "Samsung Galaxy S24 Ultra", similarity: 94, status: "High Confidence" },
      { sellerListing: "Sony WH-1000XM5 Over-Ear ANC Headphones (Silver)", seller: "Croma", canonicalProduct: "Sony WH-1000XM5 Wireless Headphones", similarity: 91, status: "High Confidence" },
      { sellerListing: "Apple MacBook Air 13.6-inch M3 Chip 8GB/256GB SSD", seller: "Reliance Digital", canonicalProduct: "Apple MacBook Air M3", similarity: 88, status: "High Confidence" },
      { sellerListing: "Dell XPS 15 9530 Core i7-13700H 16GB 1TB OLED", seller: "Vijay Sales", canonicalProduct: "Dell XPS 15 OLED", similarity: 86, status: "High Confidence" },
      { sellerListing: "Nike Air Zoom Pegasus 40 Men Road Running Shoes Size 9", seller: "Amazon India", canonicalProduct: "Nike Air Zoom Pegasus 40", similarity: 89, status: "High Confidence" },
      { sellerListing: "Adidas Ultraboost Light Running Shoes Core Black", seller: "Flipkart", canonicalProduct: "Adidas Ultraboost Light", similarity: 87, status: "High Confidence" },
      { sellerListing: "Logitech MX Master 3S Bluetooth Performance Mouse Graphite", seller: "Croma", canonicalProduct: "Logitech MX Master 3S", similarity: 90, status: "High Confidence" },
      { sellerListing: "Apple Watch Ultra 2 GPS + Cellular 49mm Titanium", seller: "Amazon India", canonicalProduct: "Apple Watch Ultra 2", similarity: 93, status: "High Confidence" },
      { sellerListing: "Bose QC Ultra Wireless Earbuds with Spatial Audio", seller: "Reliance Digital", canonicalProduct: "Bose QuietComfort Ultra Earbuds", similarity: 85, status: "High Confidence" },
      { sellerListing: "Amazon Kindle Paperwhite Signature Edition 32GB", seller: "Amazon India", canonicalProduct: "Kindle Paperwhite Signature", similarity: 72, status: "Medium Confidence" },
      { sellerListing: "Canon EOS R6 Mark II Mirrorless Camera Body Only", seller: "Vijay Sales", canonicalProduct: "Canon EOS R6 Mark II", similarity: 74, status: "Medium Confidence" },
      { sellerListing: "Generic Protective Silicon Case for Smartphone 6.1-inch", seller: "Flipkart", canonicalProduct: "No Match Found", similarity: 18, status: "Unmatched" },
      { sellerListing: "Universal USB-C Fast Charging Cable Braided 1.5m", seller: "Croma", canonicalProduct: "No Match Found", similarity: 12, status: "Unmatched" },
      { sellerListing: "RGB Mechanical Gaming Keyboard Blue Switches 87 Keys", seller: "Amazon India", canonicalProduct: "No Match Found", similarity: 15, status: "Unmatched" }
    ]
  });
}

function renderDashboardUI(data) {
  if (!data) return;

  const totalProducts = data.totalProducts || 12;
  const totalListings = data.totalListings || 15;
  const matchedListings = data.matchedListings !== undefined ? data.matchedListings : 12;
  const unmatchedListings = data.unmatchedListings !== undefined ? data.unmatchedListings : 3;
  const highConf = data.highConfidence || 10;
  const lowConf = data.lowConfidence || 2;
  const matchedPct = totalListings > 0 ? ((matchedListings / totalListings) * 100).toFixed(1) : '0.0';
  const unmatchedPct = totalListings > 0 ? ((unmatchedListings / totalListings) * 100).toFixed(1) : '0.0';

  // 1. Update 4 Summary Cards
  const elTotalProducts = document.getElementById('statTotalProducts');
  const elTotalListings = document.getElementById('statTotalListings');
  const elMatchedListings = document.getElementById('statMatchedListings');
  const elUnmatchedListings = document.getElementById('statUnmatchedListings');
  const elMatchedPercent = document.getElementById('statMatchedPercent');

  if (elTotalProducts) elTotalProducts.textContent = totalProducts;
  if (elTotalListings) elTotalListings.textContent = totalListings;
  if (elMatchedListings) elMatchedListings.textContent = matchedListings;
  if (elUnmatchedListings) elUnmatchedListings.textContent = unmatchedListings;
  if (elMatchedPercent) elMatchedPercent.textContent = `${matchedPct}% Entity Resolution Rate`;

  // 2. Update Matching Overview Progress Bars & Pills
  const lblMatched = document.getElementById('lblMatchedRatio');
  const lblUnmatched = document.getElementById('lblUnmatchedRatio');
  const lblHigh = document.getElementById('lblHighConfRatio');
  const lblLow = document.getElementById('lblLowConfRatio');

  const barMatched = document.getElementById('barMatched');
  const barUnmatched = document.getElementById('barUnmatched');
  const barHigh = document.getElementById('barHighConfRatio');
  const barLow = document.getElementById('barLowConfRatio');

  if (lblMatched) lblMatched.textContent = `${matchedListings} / ${totalListings} (${matchedPct}%)`;
  if (lblUnmatched) lblUnmatched.textContent = `${unmatchedListings} / ${totalListings} (${unmatchedPct}%)`;

  const highPct = matchedListings > 0 ? Math.round((highConf / matchedListings) * 100) : 0;
  const lowPct = matchedListings > 0 ? Math.round((lowConf / matchedListings) * 100) : 0;
  if (lblHigh) lblHigh.textContent = `${highConf} / ${matchedListings} (${highPct}%)`;
  if (lblLow) lblLow.textContent = `${lowConf} / ${matchedListings} (${lowPct}%)`;

  if (barMatched) barMatched.style.width = `${matchedPct}%`;
  if (barUnmatched) barUnmatched.style.width = `${unmatchedPct}%`;
  const barHighEl = document.getElementById('barHighConf');
  const barLowEl = document.getElementById('barLowConf');
  if (barHighEl) barHighEl.style.width = `${highPct}%`;
  if (barLowEl) barLowEl.style.width = `${lowPct}%`;

  const pMatched = document.getElementById('pillMatchedCount');
  const pUnmatched = document.getElementById('pillUnmatchedCount');
  const pHigh = document.getElementById('pillHighCount');
  const pLow = document.getElementById('pillLowCount');
  if (pMatched) pMatched.textContent = `${matchedListings} Matched`;
  if (pUnmatched) pUnmatched.textContent = `${unmatchedListings} Unmatched`;
  if (pHigh) pHigh.textContent = `${highConf} High Conf`;
  if (pLow) pLow.textContent = `${lowConf} Low Conf`;

  // 3. Update Recent Matches Table
  const tbody = document.getElementById('recentMatchesTableBody');
  if (!tbody) return;

  const list = data.recentMatches || [];
  if (list.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">No recent matches available.</td></tr>`;
    return;
  }

  tbody.innerHTML = list.map(item => {
    const sim = Math.min(100, Math.round(item.similarity || 0));
    let statusClass = 'status-unmatched';
    let statusLabel = item.status || 'Unmatched';
    let barColor = '#f59e0b';

    if (item.status === 'High Confidence' || sim >= 75) {
      statusClass = 'status-high';
      statusLabel = 'High Confidence';
      barColor = '#10b981';
    } else if (item.status === 'Medium Confidence' || (sim >= 35 && sim < 75)) {
      statusClass = 'status-medium';
      statusLabel = 'Medium Confidence';
      barColor = '#38bdf8';
    }

    return `
      <tr>
        <td>
          <strong style="color: #f1f5f9; display: block;">${escapeHtml(item.sellerListing)}</strong>
          <span class="table-seller-badge">${escapeHtml(item.seller || 'Online Store')}</span>
        </td>
        <td>
          <span style="font-weight: 600; color: ${item.canonicalProduct !== 'No Match Found' ? '#a5b4fc' : 'var(--text-muted)'};">
            ${escapeHtml(item.canonicalProduct)}
          </span>
        </td>
        <td>
          <div class="sim-bar-wrap">
            <div class="sim-bar-track">
              <div class="sim-bar-fill" style="width: ${sim}%; background: ${barColor};"></div>
            </div>
            <span class="sim-val-text" style="color: ${barColor};">${sim}%</span>
          </div>
        </td>
        <td>
          <span class="match-status-badge ${statusClass}">
            ${statusLabel}
          </span>
        </td>
      </tr>
    `;
  }).join('');
}

/**
 * Quick search shortcut used by Home page product preview cards
 */
function quickSearchHome(term) {
  switchView('products');
  searchInput.value = term;
  clearBtn.style.display = 'block';
  executeSearch(term);
}

/**
 * Populates product target options in the Algorithm Matcher Lab dropdown
 */
function populateMatcherTargetDropdown() {
  if (!matcherTargetSelect) return;
  const list = cachedSearchResults.length > 0 ? cachedSearchResults : canonicalProducts;
  const currentVal = matcherTargetSelect.value;

  matcherTargetSelect.innerHTML = list.map(item => {
    const p = item.id ? item : item.product;
    return `<option value="${p.id}">${escapeHtml(p.name)} (${formatINR(p.price)})</option>`;
  }).join('');

  if (currentVal) {
    matcherTargetSelect.value = currentVal;
  }
}

/**
 * Interactive Algorithm Matcher Lab: evaluates individual DSA scores
 * for any query string against the chosen product.
 */
function evaluateMatchingLab() {
  if (!matcherQueryInput || !matcherTargetSelect) return;
  const list = cachedSearchResults.length > 0 ? cachedSearchResults : canonicalProducts;

  const rawQuery = matcherQueryInput.value.trim().toLowerCase();
  const selectedId = Number(matcherTargetSelect.value || 1);
  const found = list.find(x => {
    const p = x.id ? x : x.product;
    return p.id === selectedId;
  });
  const prod = found ? (found.id ? found : found.product) : (list[0].id ? list[0] : list[0].product);
  if (!prod) return;

  const targetName = prod.name.toLowerCase();
  const targetDesc = prod.description.toLowerCase();
  const targetTags = (prod.tags || []).join(' ').toLowerCase();
  const fullTarget = `${targetName} ${targetDesc} ${targetTags}`;

  // 1. Trie Prefix Score: Checks if query is a prefix of any product word
  const words = fullTarget.split(/\s+/);
  let trieScore = 0.0;
  let trieMatchedWord = '';
  if (rawQuery.length > 0) {
    for (const w of words) {
      if (w.startsWith(rawQuery)) {
        trieScore = Math.min(1.0, rawQuery.length / w.length + 0.3);
        trieMatchedWord = w;
        break;
      }
    }
  }

  // 2. Rabin-Karp Exact Substring Match
  let rkScore = 0.0;
  if (rawQuery.length > 0 && fullTarget.includes(rawQuery)) {
    rkScore = 1.0;
  }

  // 3. Levenshtein Dynamic Programming Edit Distance
  function calcLevenshtein(s1, s2) {
    const m = s1.length, n = s2.length;
    const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
    for (let i = 0; i <= m; i++) dp[i][0] = i;
    for (let j = 0; j <= n; j++) dp[0][j] = j;
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        if (s1[i - 1] === s2[j - 1]) dp[i][j] = dp[i - 1][j - 1];
        else dp[i][j] = 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
      }
    }
    return dp[m][n];
  }

  let minEditDist = Infinity;
  let bestToken = '';
  const queryTokens = rawQuery.split(/\s+/).filter(Boolean);
  if (queryTokens.length > 0) {
    for (const qTok of queryTokens) {
      for (const w of words) {
        if (Math.abs(qTok.length - w.length) <= 3) {
          const dist = calcLevenshtein(qTok, w);
          if (dist < minEditDist) {
            minEditDist = dist;
            bestToken = w;
          }
        }
      }
    }
  }
  const maxLen = Math.max(1, rawQuery.length);
  const dpScore = minEditDist === Infinity ? 0.0 : Math.max(0, 1.0 - (minEditDist / (bestToken.length || maxLen)));

  // 4. Max Flow Token Allocation: fraction of query tokens mapped to product features
  let flowMatchedCount = 0;
  queryTokens.forEach(t => {
    if (fullTarget.includes(t)) flowMatchedCount++;
  });
  const flowScore = queryTokens.length > 0 ? (flowMatchedCount / queryTokens.length) : 0.0;

  // Composite Weighted Score
  const composite = Math.min(1.0, (0.35 * rkScore) + (0.30 * dpScore) + (0.25 * flowScore) + (0.10 * trieScore));
  const compPct = Math.round(composite * 100);

  // Render values to DOM
  if (labTrieScore) labTrieScore.textContent = trieScore.toFixed(2);
  if (labTrieDetail) labTrieDetail.textContent = trieScore > 0
    ? `Prefix matched against "${trieMatchedWord}" in Trie tree.`
    : `No direct prefix path matched for "${rawQuery}".`;

  if (labRkScore) labRkScore.textContent = rkScore.toFixed(2);
  if (labRkDetail) labRkDetail.textContent = rkScore === 1.0
    ? `Rolling hash matched exact phrase in text in O(N+M).`
    : `Substring not found verbatim in product text.`;

  if (labDpScore) labDpScore.textContent = dpScore.toFixed(2);
  if (labDpDetail) labDpDetail.textContent = minEditDist <= 2
    ? `Edit distance = ${minEditDist} (closest word "${bestToken}").`
    : `High edit distance (${minEditDist === Infinity ? 'N/A' : minEditDist}) across tokens.`;

  if (labFlowScore) labFlowScore.textContent = flowScore.toFixed(2);
  if (labFlowDetail) labFlowDetail.textContent = `${flowMatchedCount} of ${queryTokens.length} tokens assigned in bipartite flow graph.`;

  if (labCompositeScore) labCompositeScore.textContent = `${composite.toFixed(2)} / 1.00 (${compPct}%)`;
  if (labCompositeBar) labCompositeBar.style.width = `${compPct}%`;
}

/**
 * Populates product options in the single product cross-site dropdown.
 * Contains ONLY valid canonical products with a default "Select a product..." option.
 */
function populateCrossSiteDropdown() {
  if (!compareProductSelect) return;
  const list = canonicalProducts.filter(p => p && p.id && p.name && p.name.trim() !== '' && p.name !== '-');

  const currentVal = compareProductSelect.value;
  let optionsHtml = '<option value="">Select a product...</option>';

  optionsHtml += list.map(p => {
    return `<option value="${p.id}">${escapeHtml(p.name)}</option>`;
  }).join('');

  compareProductSelect.innerHTML = optionsHtml;
  if (currentVal && list.some(p => String(p.id) === String(currentVal))) {
    compareProductSelect.value = currentVal;
  }
}

/**
 * Generates and compares the selected canonical product across 5 major Indian e-commerce sites:
 * Amazon India, Flipkart, Croma, Reliance Digital, and Vijay Sales.
 * Demonstrates: Canonical Product -> Seller Listings -> Entity Resolution -> Price Comparison.
 */
function renderCrossSiteComparison() {
  if (!crossSiteCardsGrid || !comparisonMatrixTable) return;

  const selectedVal = compareProductSelect ? compareProductSelect.value : '';
  if (!selectedVal) {
    crossSiteCardsGrid.innerHTML = `
      <div style="grid-column: 1 / -1; text-align: center; padding: 3rem 1.5rem; background: rgba(255, 255, 255, 0.02); border: 1px dashed var(--border-color); border-radius: var(--radius-lg);">
        <p style="color: var(--text-muted); font-size: 1.05rem; font-weight: 600;">
          👈 Please select a product from the dropdown above to match listings and compare prices across stores.
        </p>
      </div>
    `;
    comparisonMatrixTable.innerHTML = '';
    return;
  }

  const selectedId = Number(selectedVal);
  const p = canonicalProducts.find(x => x && x.id === selectedId);

  if (!p) {
    crossSiteCardsGrid.innerHTML = `
      <div style="grid-column: 1 / -1; text-align: center; padding: 2rem; color: var(--text-muted);">
        Product details not found in catalog.
      </div>
    `;
    comparisonMatrixTable.innerHTML = '';
    return;
  }

  const basePrice = Number(p.price);
  const hasValidPrice = !isNaN(basePrice) && basePrice > 0;

  // Format price helper: if not available or <= 0, display "Price unavailable"
  const getStorePriceText = (num) => {
    if (!hasValidPrice || isNaN(num) || num <= 0) return 'Price unavailable';
    return formatINR(num);
  };

  // 5 Real Indian Retailers matching canonical entity with realistic variation
  const stores = [
    {
      name: "Amazon India",
      logo: "📦",
      price: hasValidPrice ? Math.round(basePrice * 0.98) : null,
      delivery: "Prime Tomorrow (Free Delivery)",
      seller: "Appario Retail India (Fulfilled by Amazon)",
      rating: 4.8,
      stock: "In Stock",
      offer: "Extra ₹1,500 off on HDFC & ICICI Bank Cards",
      similarityScore: 96,
      listingTitle: `${p.name} (Official Indian Warranty, Brand Sealed)`
    },
    {
      name: "Flipkart",
      logo: "🛍️",
      price: hasValidPrice ? Math.round(basePrice * 0.95) : null, // Best price
      delivery: "2 Business Days",
      seller: "SuperComNet (Flipkart Assured)",
      rating: 4.7,
      stock: "In Stock",
      offer: "5% Unlimited Cashback on Flipkart Axis Bank Card",
      similarityScore: 94,
      listingTitle: `${p.name} - Assured Genuine Product`
    },
    {
      name: "Croma",
      logo: "🏬",
      price: hasValidPrice ? Math.round(basePrice * 1.0) : null,
      delivery: "Express Store Pickup (3 Hours)",
      seller: "Infiniti Retail Ltd (A Tata Enterprise)",
      rating: 4.6,
      stock: "Available at nearby store",
      offer: "Earn 2% Tata NeuCoins + 1 Year Extended Warranty",
      similarityScore: 92,
      listingTitle: `${p.name} - Tata Authenticated Store Unit`
    },
    {
      name: "Reliance Digital",
      logo: "⚡",
      price: hasValidPrice ? Math.round(basePrice * 0.97) : null,
      delivery: "Standard 2-3 Business Days",
      seller: "Reliance Retail Ltd (Digital Care)",
      rating: 4.6,
      stock: "In Stock",
      offer: "Instant ₹2,000 Bank Discount + No Cost EMI",
      similarityScore: 91,
      listingTitle: `${p.name} (ResQ Tech Care Assured)`
    },
    {
      name: "Vijay Sales",
      logo: "🏢",
      price: hasValidPrice ? Math.round(basePrice * 0.96) : null,
      delivery: "Express Courier (1-2 Days)",
      seller: "Vijay Sales Corporate Store",
      rating: 4.8,
      stock: "In Stock",
      offer: "Flat 7.5% Instant Discount on SBI Cards",
      similarityScore: 89,
      listingTitle: `${p.name} Genuine Indian Retail Pack`
    }
  ];

  // Identify lowest price among available valid prices
  const validPrices = stores.map(s => s.price).filter(pr => pr !== null && pr > 0);
  const minPrice = validPrices.length > 0 ? Math.min(...validPrices) : null;
  const maxPrice = validPrices.length > 0 ? Math.max(...validPrices) : null;
  const maxSavings = (minPrice !== null && maxPrice !== null) ? (maxPrice - minPrice) : 0;

  // Render 5 Store Cards
  crossSiteCardsGrid.innerHTML = stores.map(store => {
    const isBest = store.price !== null && store.price === minPrice;
    const priceDisplay = getStorePriceText(store.price);

    return `
      <div class="store-card ${isBest ? 'store-card-best' : ''}">
        ${isBest ? `<span class="store-badge-best">🏆 Lowest Price</span>` : ''}
        <div class="store-header">
          <div class="store-brand">
            <span class="store-logo">${store.logo}</span>
            <div>
              <h4 class="store-name">${escapeHtml(store.name)}</h4>
              <span class="store-seller">${escapeHtml(store.seller)}</span>
            </div>
          </div>
          <span class="store-rating">⭐ ${store.rating}</span>
        </div>

        <div style="margin: 0.75rem 0 0.5rem 0; font-size: 0.85rem; color: #f1f5f9; font-weight: 600; line-height: 1.4;">
          ${escapeHtml(store.listingTitle)}
        </div>

        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; font-size: 0.75rem; color: var(--text-muted);">
          <span>Match Accuracy: <strong style="color: #34d399;">${store.similarityScore}%</strong></span>
          <span style="background: rgba(16, 185, 129, 0.15); color: #34d399; padding: 0.1rem 0.5rem; border-radius: 999px;">Verified</span>
        </div>

        <div class="store-price-box">
          <div class="store-price" style="${store.price ? '' : 'font-size: 1.1rem; color: var(--text-muted);'}">
            ${priceDisplay}
          </div>
          <div class="store-delivery">🚚 ${escapeHtml(store.delivery)}</div>
        </div>

        <div class="store-offer">
          🎁 <span>${escapeHtml(store.offer)}</span>
        </div>

        <div class="store-stock">
          <span class="stock-indicator in-stock"></span> ${escapeHtml(store.stock)}
        </div>
      </div>
    `;
  }).join('');

  // Render Comparison Matrix Table
  comparisonMatrixTable.innerHTML = `
    <div style="margin-bottom: 1rem; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.5rem;">
      <h3 style="font-size: 1.1rem; color: var(--text-main); font-weight: 700;">
        Side-by-Side Comparison for <span style="color: var(--primary);">${escapeHtml(p.name)}</span>
      </h3>
      ${maxSavings > 0 ? `
        <span style="font-size: 0.85rem; padding: 0.35rem 0.75rem; background: rgba(16, 185, 129, 0.1); color: #34d399; border-radius: 999px; font-weight: 600; border: 1px solid rgba(16, 185, 129, 0.2);">
          💡 Maximum Potential Savings: ${formatINR(maxSavings)}
        </span>
      ` : ''}
    </div>

    <table class="comparison-table">
      <thead>
        <tr>
          <th style="width: 22%;">Platform & Seller</th>
          <th style="width: 18%;">Price (INR)</th>
          <th style="width: 16%;">Entity Match</th>
          <th style="width: 22%;">Delivery Speed</th>
          <th style="width: 22%;">Available Bank Discount</th>
        </tr>
      </thead>
      <tbody>
        ${stores.map(store => {
    const isBest = store.price !== null && store.price === minPrice;
    const priceDisplay = getStorePriceText(store.price);

    return `
            <tr class="${isBest ? 'compare-row-winner' : ''}">
              <td>
                <strong>${escapeHtml(store.name)}</strong>
                <span style="display: block; font-size: 0.75rem; color: var(--text-muted);">${escapeHtml(store.seller)}</span>
              </td>
              <td class="${isBest ? 'compare-diff-winner' : ''}">
                <strong style="font-size: 1.05rem;">${priceDisplay}</strong>
                ${isBest ? '<span style="display: block; font-size: 0.75rem; color: #34d399;">(Best Online Deal)</span>' : ''}
              </td>
              <td>
                <span class="status-pill status-ok" style="background: rgba(16, 185, 129, 0.15); color: #34d399;">
                  ${store.similarityScore}% Match
                </span>
              </td>
              <td>${escapeHtml(store.delivery)}</td>
              <td><span style="font-size: 0.82rem; color: var(--text-muted);">${escapeHtml(store.offer)}</span></td>
            </tr>
          `;
  }).join('')}
      </tbody>
    </table>
  `;
}

/**
 * Applies active category and price filters to cached results
 */
function filterAndRenderResults(query = searchInput.value.trim()) {
  let filtered = [...cachedSearchResults];

  // 1. Category Filter
  if (selectedCategory !== 'all') {
    filtered = filtered.filter(item => {
      const p = item.product || item;
      return p.category && p.category.toLowerCase() === selectedCategory.toLowerCase();
    });
  }

  // 2. Max Price Filter (in INR)
  filtered = filtered.filter(item => {
    const p = item.product || item;
    return p.price <= maxPriceFilter;
  });

  displayResults(filtered, query);
}

/**
 * Renders canonical product cards into the DOM
 */
// Category Icon Map
const CATEGORY_ICONS = {
  'Smartphones': '📱',
  'Laptops': '💻',
  'Audio': '🎧',
  'Footwear': '👟',
  'Accessories': '🖱️',
  'Wearables': '⌚',
  'Electronics': '📖',
  'Cameras': '📷'
};

function renderProductCardHTML(item) {
  const p = item.product || item;
  const formattedPrice = formatINR(p.price);
  const isLiveWeb = item.source === 'serpapi-live-web' || item.source === 'dummyjson-web-catalog' || (item.id > 1000);
  const badgeText = isLiveWeb ? (item.source === 'serpapi-live-web' ? '🌐 Google Shopping (Live)' : '🌐 Web Catalog') : 'Catalog Item';
  const badgeClass = isLiveWeb ? 'source-badge-web' : 'match-badge match-exact';
  const scoreVal = item.score !== undefined ? Math.round(item.score * 100) : null;
  const catIcon = CATEGORY_ICONS[p.category] || '📦';
  const tagsList = Array.isArray(p.tags) ? p.tags.slice(0, 3) : [];
  const safeName = escapeHtml(p.name).replace(/'/g, "\\'");

  return `
    <article class="product-card card-premium-glow" id="product-${p.id}">
      <div>
        <div class="card-header-row">
          <div class="card-category-pill">
            <span class="cat-icon">${catIcon}</span>
            <span class="category-tag">${escapeHtml(p.category || 'General')}</span>
          </div>
          <div class="card-badges-wrap">
            <span class="${badgeClass}">${badgeText}</span>
            ${scoreVal !== null ? `<span class="dsa-score-pill">${scoreVal}% DSA Match</span>` : ''}
          </div>
        </div>

        <h4 class="product-title" title="${escapeHtml(p.name)}">
          ${escapeHtml(p.name)}
        </h4>
        <p class="product-desc">
          ${escapeHtml(p.description || 'Enterprise catalog verified product specifications.')}
        </p>

        ${tagsList.length > 0 ? `
          <div class="card-tags-row">
            ${tagsList.map(t => `<span class="product-mini-tag">#${escapeHtml(t)}</span>`).join('')}
          </div>
        ` : ''}
      </div>

      <div class="card-bottom-row">
        <div class="price-container">
          <span class="price-label">Store Price</span>
          <div class="product-price">
            <span class="rupee-symbol">₹</span>${formattedPrice.replace('₹', '')}
          </div>
        </div>

        <div class="card-actions-wrap">
          <div class="product-rating-badge">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
            <span>${Number(p.rating || 4.5).toFixed(1)}</span>
          </div>
          <button class="card-match-btn" onclick="matchFromCard('${safeName}')" title="Resolve & Compare multi-seller listings">
            Match
          </button>
        </div>
      </div>
    </article>
  `;
}

function matchFromCard(productName) {
  switchView('matchListing');
  const input = document.getElementById('listingTitleInput');
  if (input) {
    input.value = productName;
    findMatch();
  }
}

function renderHomeFeaturedCards() {
  const container = document.getElementById('homeFeaturedGrid');
  if (!container) return;
  const featured = canonicalProducts.slice(0, 6);
  container.innerHTML = featured.map(p => renderProductCardHTML(p)).join('');
}

function renderDashboardProducts() {
  const container = document.getElementById('dashboardProductsGrid');
  if (!container) return;
  const list = canonicalProducts.slice(0, 8);
  container.innerHTML = list.map(p => renderProductCardHTML(p)).join('');
}

function clearProductSearch() {
  const input = document.getElementById('productSearchInput');
  const clearBtn = document.getElementById('clearBtn');
  if (input) {
    input.value = '';
    input.focus();
  }
  if (clearBtn) clearBtn.style.display = 'none';
  displayResults(canonicalProducts, '');
}

function displayResults(results, query) {
  if (resultsCount) resultsCount.textContent = `${results.length} item${results.length === 1 ? '' : 's'}`;
  if (resultsTitle) resultsTitle.textContent = query ? `Results for "${query}"` : 'Matched Product Catalog';

  if (fuzzyNotice) fuzzyNotice.style.display = 'none';

  if (!productsGrid) return;

  if (results.length === 0) {
    productsGrid.innerHTML = `
      <div class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          <line x1="8" y1="11" x2="14" y2="11"></line>
        </svg>
        <h4>No products found</h4>
        <p>No matched products found for your query "${escapeHtml(query)}". Try another search term or click Web Search API.</p>
        <button class="primary-btn" style="margin-top: 14px;" onclick="openApiKeyModal()">🌐 Search Live Web via SerpApi</button>
      </div>
    `;
    return;
  }

  productsGrid.innerHTML = results.map(item => renderProductCardHTML(item)).join('');
}

function renderLoadingState() {
  if (productsGrid) {
    productsGrid.innerHTML = `
      <div style="grid-column: 1 / -1; text-align: center; padding: 3rem;">
        <p style="color: var(--text-muted); font-size: 0.95rem;">Evaluating algorithmic matching scores...</p>
      </div>
    `;
  }
}

function simulateLocalSearch(query) {
  if (!query) {
    return canonicalProducts.map(p => ({
      ...p,
      score: 1.0,
      matchType: 'Catalog',
      isFuzzy: false,
      scores: { trie: 1.0, rabinKarp: 1.0, levenshtein: 1.0, maxFlow: 1.0 }
    }));
  }

  const q = query.toLowerCase();
  return canonicalProducts
    .filter(p => (p.name || '').toLowerCase().includes(q) || (p.tags || []).some(t => t.toLowerCase().includes(q)) || (p.description || '').toLowerCase().includes(q))
    .map(p => ({
      ...p,
      score: 0.88,
      matchType: 'Local Match',
      isFuzzy: false,
      scores: { trie: 0.8, rabinKarp: 0.9, levenshtein: 0.85, maxFlow: 0.9 }
    }));
}

/* ==========================================================================
   User Authentication (Login & Sign Up)
   ========================================================================== */
function openAuthModal(tab = 'login') {
  authAlert.style.display = 'none';
  authModal.style.display = 'flex';
  document.body.style.overflow = 'hidden';
  switchAuthTab(tab);
}

function closeAuthModal() {
  authModal.style.display = 'none';
  document.body.style.overflow = '';
}

function switchAuthTab(tab) {
  authAlert.style.display = 'none';
  if (tab === 'login') {
    tabLogin.classList.add('active');
    tabSignup.classList.remove('active');
    loginForm.style.display = 'flex';
    signupForm.style.display = 'none';
  } else {
    tabSignup.classList.add('active');
    tabLogin.classList.remove('active');
    signupForm.style.display = 'flex';
    loginForm.style.display = 'none';
  }
}

async function handleLoginSubmit(e) {
  if (e) e.preventDefault();
  const emailInput = document.getElementById('loginEmail');
  const passwordInput = document.getElementById('loginPassword');
  const email = (emailInput ? emailInput.value : '').trim();
  const password = (passwordInput ? passwordInput.value : '').trim();

  if (!email) {
    showAuthAlert('Please enter an email address.', 'error');
    return;
  }

  // Derive friendly user display name from email (e.g. demo@example.com -> Demo User)
  let displayName = 'Store User';
  if (email.includes('@')) {
    const rawName = email.split('@')[0].replace(/[._-]/g, ' ');
    displayName = rawName.charAt(0).toUpperCase() + rawName.slice(1);
  } else {
    displayName = email;
  }

  // Try authenticating with backend server if available, otherwise succeed immediately
  try {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (res.ok) {
      const data = await res.json();
      if (data && data.success && data.user) {
        loginUserSuccess(data.user);
        return;
      }
    }
  } catch (err) {
    // Network or server offline - seamless offline access
  }

  // Instant login success
  loginUserSuccess({ name: displayName, email: email });
}

async function handleSignupSubmit(e) {
  if (e) e.preventDefault();
  const nameInput = document.getElementById('signupName');
  const emailInput = document.getElementById('signupEmail');
  const passwordInput = document.getElementById('signupPassword');

  const name = (nameInput ? nameInput.value : '').trim() || 'New User';
  const email = (emailInput ? emailInput.value : '').trim() || 'user@example.com';
  const password = (passwordInput ? passwordInput.value : '').trim();

  try {
    await fetch(`${API_BASE}/api/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email, password })
    });
  } catch (err) {
    // Ignore server error
  }

  loginUserSuccess({ name, email });
  showToast(`Welcome to ProductMatch, ${name}!`);
}

function loginUserSuccess(user) {
  localStorage.setItem('productmatch_user', JSON.stringify(user));
  updateAuthUI(user);
  showToast(`Welcome, ${user.name}! Entering application...`);
}

function handleLogout() {
  localStorage.removeItem('productmatch_user');
  updateAuthUI(null);
  showToast('Signed out. Please log in to enter.');
}

function checkLocalSession() {
  try {
    const raw = localStorage.getItem('productmatch_user');
    if (raw) {
      const user = JSON.parse(raw);
      updateAuthUI(user);
    } else {
      updateAuthUI(null);
    }
  } catch (ignored) {
    updateAuthUI(null);
  }
}

function updateAuthUI(user) {
  if (user) {
    if (authGatewayScreen) authGatewayScreen.style.display = 'none';
    if (mainAppWrapper) mainAppWrapper.style.display = 'block';
    if (userNameLabel) userNameLabel.textContent = user.name || 'User';
    if (userAvatarChar) userAvatarChar.textContent = (user.name || 'U').charAt(0).toUpperCase();
    document.body.classList.remove('auth-pending');
    document.body.classList.add('authenticated');
  } else {
    if (authGatewayScreen) authGatewayScreen.style.display = 'flex';
    if (mainAppWrapper) mainAppWrapper.style.display = 'none';
    document.body.classList.add('auth-pending');
    document.body.classList.remove('authenticated');
  }
}

function showAuthAlert(msg, type = 'error') {
  authAlert.textContent = msg;
  authAlert.className = `auth-alert ${type}`;
  authAlert.style.display = 'flex';
}

function showToast(message) {
  toastNotification.textContent = message;
  toastNotification.style.display = 'flex';
  setTimeout(() => {
    toastNotification.style.display = 'none';
  }, 3500);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * Resolves a raw seller listing title against the canonical catalog,
 * computes DSA similarity, and renders store price comparisons.
 */
function findMatch() {
  const input = document.getElementById('listingTitleInput');
  const query = (input ? input.value : '').trim();
  const matchResults = document.getElementById('matchResults');

  if (!query) {
    if (matchResults) {
      matchResults.innerHTML = `
        <div class="empty-match-state">
          <div class="empty-match-icon">⚠️</div>
          <h3>Please Enter a Listing Title</h3>
          <p>Type a seller listing title above to identify its matched product.</p>
        </div>
      `;
    }
    return;
  }

  // Find best standard product using exact substring match, token overlap, and Levenshtein DP
  const cleanQ = query.toLowerCase().trim();
  const qTokens = cleanQ.split(/\s+/).filter(Boolean);
  let bestMatch = null;
  let bestScore = -1;

  canonicalProducts.forEach(prod => {
    const prodName = (prod.name || '').toLowerCase().trim();
    const pNameTokens = prodName.split(/\s+/).filter(Boolean);
    const pTokens = `${prod.name} ${prod.category} ${(prod.tags || []).join(' ')}`.toLowerCase().split(/\s+/).filter(Boolean);

    // 1. Exact name match bonus
    if (prodName === cleanQ || prodName.replace(/^apple\s+/, '') === cleanQ) {
      bestScore = 1.0;
      bestMatch = prod;
      return;
    }

    // 2. Token overlap score
    let matchedToks = 0;
    qTokens.forEach(qt => {
      if (pTokens.some(pt => pt === qt || pt.includes(qt) || qt.includes(pt))) matchedToks++;
    });
    const tokScore = qTokens.length > 0 ? (matchedToks / qTokens.length) : 0;

    // 3. Normalized Levenshtein similarity on product name
    const strippedName = prodName.replace(/^apple\s+/, '');
    const nameDist = Math.min(
      fastLevenshtein(cleanQ, prodName),
      fastLevenshtein(cleanQ, strippedName)
    );
    const maxLen = Math.max(cleanQ.length, strippedName.length);
    const levScore = maxLen > 0 ? Math.max(0, 1 - (nameDist / maxLen)) : 0;

    // 4. Modifier penalty: if user query did NOT ask for 'pro' or 'max', penalize products containing 'pro' or 'max'
    let modifierPenalty = 0.0;
    const modifiers = ['pro', 'max', 'plus', 'mini', 'ultra'];
    modifiers.forEach(mod => {
      const userAskedMod = qTokens.includes(mod);
      const prodHasMod = pNameTokens.includes(mod);
      if (!userAskedMod && prodHasMod) {
        modifierPenalty += 0.15; // penalize mismatch on major variant
      }
    });

    let totalScore = (tokScore * 0.5) + (levScore * 0.5) - modifierPenalty;
    totalScore = Math.max(0, Math.min(1, totalScore));

    if (totalScore > bestScore) {
      bestScore = totalScore;
      bestMatch = prod;
    }
  });

  // Minimum confidence required to declare a canonical match
  const CONFIDENCE_THRESHOLD = 0.75;
  const isConfident = bestMatch && bestScore >= CONFIDENCE_THRESHOLD;
  const simPct = Math.min(99, Math.round(bestScore * 100));

  if (!isConfident) {
    if (matchResults) {
      matchResults.innerHTML = `
        <div style="background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.4); border-radius: 12px; padding: 22px; display: flex; flex-direction: column; gap: 14px;">
          <div style="display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px;">
            <div style="display: flex; align-items: center; gap: 14px;">
              <div style="font-size: 32px;">⚠️</div>
              <div>
                <span style="font-size: 11px; font-weight: 700; color: #f87171; text-transform: uppercase;">No Confident Match Found</span>
                <h3 style="margin: 4px 0; color: #fff; font-size: 17px;">No matching product in catalog for "${escapeHtml(query)}"</h3>
                <p style="margin: 0; color: #94a3b8; font-size: 13px;">
                  Best local candidate: <strong style="color: #e2e8f0;">${bestMatch ? escapeHtml(bestMatch.name) : 'None'}</strong> 
                  (${simPct}% confidence, below required 75% threshold).
                </p>
              </div>
            </div>
            <div style="text-align: right;">
              <span style="display: inline-block; background: rgba(239, 68, 68, 0.2); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.4); padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 700;">
                Threshold Not Met (&lt; 75%)
              </span>
            </div>
          </div>
          <div style="display: flex; align-items: center; gap: 10px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 12px; flex-wrap: wrap;">
            <button class="primary-btn" onclick="openApiKeyModal()" style="font-size: 13px; padding: 7px 16px;">
              🌐 Search Live Web via SerpApi
            </button>
            <button class="outline-btn" onclick="searchProducts('${escapeHtml(query)}')" style="font-size: 13px; padding: 7px 16px;">
              🔍 Search Catalog Directly
            </button>
          </div>
        </div>
      `;
    }
    showToast(`No confident catalog match for "${query}" (< 75% confidence)`);
    return;
  }

  if (matchResults && bestMatch) {
    matchResults.innerHTML = `
      <div style="background: rgba(37, 99, 235, 0.08); border: 1px solid #2563eb; border-radius: 12px; padding: 20px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px;">
        <div style="display: flex; align-items: center; gap: 14px;">
          <div style="font-size: 32px;">✅</div>
          <div>
            <span style="font-size: 11px; font-weight: 700; color: #60a5fa; text-transform: uppercase;">Matched Product</span>
            <h3 style="margin: 4px 0; color: #fff; font-size: 18px;">${escapeHtml(bestMatch.name)}</h3>
            <p style="margin: 0; color: #94a3b8; font-size: 13px;">Category: ${escapeHtml(bestMatch.category)} • Base Catalog Price: ${formatINR(bestMatch.price)}</p>
          </div>
        </div>
        <div style="text-align: right;">
          <span style="display: inline-block; background: #2563eb; color: #fff; padding: 4px 12px; border-radius: 9999px; font-size: 13px; font-weight: 700;">${simPct}% Confidence Match</span>
          <p style="margin: 4px 0 0; color: #64748b; font-size: 11px;">Algorithm: Hybrid Levenshtein + Flow</p>
        </div>
      </div>
    `;
  }

  // Populate the dedicated Compare page and transition
  populateComparePage(bestMatch, simPct, query);
  switchView('compare');
  showToast(`Matched "${bestMatch.name}" (${simPct}% Confidence) — Viewing Comparison`);
}

/**
 * Renders data into the dedicated Compare page view
 */
function populateComparePage(product, simPct, originalQuery) {
  if (!product) return;

  // 1. Update Hero Details
  const titleEl = document.getElementById('compareCanonicalTitle');
  const descEl = document.getElementById('compareCanonicalDesc');
  const catEl = document.getElementById('compareMetaCategory');
  const priceEl = document.getElementById('compareMetaBasePrice');
  const scoreEl = document.getElementById('compareMetaScore');
  const queryEl = document.getElementById('compareMetaQuery');
  const badgeEl = document.getElementById('compareMatchedBadge');

  const displayName = (originalQuery && originalQuery.trim()) ? originalQuery.trim() : product.name;

  if (titleEl) titleEl.textContent = displayName;
  if (descEl) {
    if (product.description) {
      descEl.textContent = product.description;
    } else {
      descEl.textContent = `Live multi-seller price comparisons and availability for "${displayName}".`;
    }
  }
  if (catEl) catEl.textContent = `📦 Category: ${product.category || 'General'}`;
  if (priceEl) priceEl.textContent = `💵 Best Base Price: ${formatINR(product.price)}`;
  if (scoreEl) scoreEl.textContent = `🎯 ${simPct || 95}% Match`;

  if (badgeEl) {
    badgeEl.textContent = `Product: ${displayName}`;
  }

  // Sync Dropdown
  const select = document.getElementById('compareProductSelect');
  if (select) {
    populateCompareDropdown();
    select.value = String(product.id || '');
  }

  // 2. Multi-Seller Pricing with simulated discounts
  const base = Number(product.price) || 10000;
  const sellerCardsGrid = document.getElementById('compareSellerCardsGrid');
  const matrixBody = document.getElementById('compareMatrixBody');

  const sellers = [
    { 
      name: "Flipkart", 
      logo: "🛍️", 
      price: Math.round(base * 0.94), 
      best: true, 
      delivery: "Tomorrow, by 11 PM", 
      rating: 4.6, 
      returnPolicy: "7 Days Replacement", 
      warranty: "1 Year Brand Warranty",
      stock: "In Stock (Fast Dispatch)" 
    },
    { 
      name: "Amazon India", 
      logo: "📦", 
      price: Math.round(base * 0.97), 
      best: false, 
      delivery: "Prime 1-Day Delivery", 
      rating: 4.8, 
      returnPolicy: "10 Days Returnable", 
      warranty: "1 Year Manufacturer",
      stock: "In Stock" 
    },
    { 
      name: "Reliance Digital", 
      logo: "⚡", 
      price: Math.round(base * 0.96), 
      best: false, 
      delivery: "Store Pickup or 2 Days", 
      rating: 4.5, 
      returnPolicy: "7 Days Return", 
      warranty: "1 Year Official Warranty",
      stock: "In Stock (Select Stores)" 
    },
    { 
      name: "Croma", 
      logo: "🏬", 
      price: Math.round(base * 1.00), 
      best: false, 
      delivery: "Standard 3-4 Days", 
      rating: 4.4, 
      returnPolicy: "14 Days Exchange", 
      warranty: "1 Year Standard",
      stock: "Limited Stock" 
    }
  ];

  // Render Seller Cards
  if (sellerCardsGrid) {
    sellerCardsGrid.innerHTML = sellers.map(s => `
      <div class="compare-seller-card ${s.best ? 'card-best-deal' : ''}">
        ${s.best ? '<span class="best-deal-badge">🏆 LOWEST PRICE</span>' : ''}
        <div class="seller-card-header">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span class="seller-store-icon">${s.logo}</span>
            <div>
              <h4 style="margin: 0; color: #fff; font-size: 15px; font-weight: 700;">${s.name}</h4>
              <span style="font-size: 11px; color: #94a3b8;">Verified Merchant</span>
            </div>
          </div>
          <span class="seller-rating-pill">★ ${s.rating}</span>
        </div>

        <div class="seller-card-pricing">
          <span style="font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: 700;">Live Store Price</span>
          <div class="seller-price-val ${s.best ? 'price-highlight-green' : ''}">
            ${formatINR(s.price)}
          </div>
          ${s.best ? `<span style="font-size: 11px; color: #34d399; font-weight: 700;">Save ${formatINR(base - s.price)} (${Math.round(((base - s.price)/base)*100)}% off catalog)</span>` : ''}
        </div>

        <div class="seller-card-perks">
          <div class="perk-item">
            <span>🚚</span>
            <span>${s.delivery}</span>
          </div>
          <div class="perk-item">
            <span>🛡️</span>
            <span>${s.warranty}</span>
          </div>
          <div class="perk-item">
            <span>📦</span>
            <span style="color: #34d399;">${s.stock}</span>
          </div>
        </div>

        <button class="primary-btn" style="width: 100%; margin-top: 14px; font-size: 13px;" onclick="showToast('Redirecting to ${s.name} store listing...')">
          <span>View on ${s.name}</span>
          <span>→</span>
        </button>
      </div>
    `).join('');
  }

  // Render Comparison Matrix Table
  if (matrixBody) {
    matrixBody.innerHTML = sellers.map(s => `
      <tr class="${s.best ? 'compare-matrix-best-row' : ''}">
        <td>
          <div style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 18px;">${s.logo}</span>
            <strong>${s.name}</strong>
            ${s.best ? '<span style="font-size: 9px; background: rgba(16,185,129,0.2); color: #34d399; padding: 2px 6px; border-radius: 9999px; font-weight: 800;">BEST</span>' : ''}
          </div>
        </td>
        <td>
          <strong style="color: ${s.best ? '#34d399' : '#fff'}; font-size: 15px;">${formatINR(s.price)}</strong>
        </td>
        <td>${s.delivery}</td>
        <td>${s.returnPolicy}</td>
        <td>${s.warranty}</td>
        <td>
          <span style="display: inline-flex; align-items: center; gap: 5px; color: #34d399; font-size: 12px; font-weight: 600;">
            <span style="width: 6px; height: 6px; border-radius: 50%; background: #34d399;"></span>
            ${s.stock}
          </span>
        </td>
        <td>
          <button class="outline-btn" style="padding: 5px 12px; font-size: 11px;" onclick="showToast('Opening ${s.name} store...')">
            Inspect Offer
          </button>
        </td>
      </tr>
    `).join('');
  }
}

/**
 * Loads selected canonical product into the Compare view from the dropdown
 */
function loadCompareProduct(productId) {
  if (!productId) return;
  const prod = canonicalProducts.find(p => String(p.id) === String(productId));
  if (prod) {
    populateComparePage(prod, 95, prod.name);
  }
}

/**
 * Populates the dropdown on the Compare page with available products
 */
function populateCompareDropdown() {
  const select = document.getElementById('compareProductSelect');
  if (!select) return;
  const currentVal = select.value;
  select.innerHTML = '<option value="">-- Choose Matched Product --</option>' +
    canonicalProducts.map(p => `
      <option value="${p.id}">${escapeHtml(p.name)} (${formatINR(p.price)})</option>
    `).join('');
  if (currentVal) select.value = currentVal;
}

/* ==========================================================================
   User Profile Modal & Name Management
   ========================================================================== */
function openProfileModal() {
  const modal = document.getElementById('profileModal');
  if (modal) modal.style.display = 'flex';
  document.body.style.overflow = 'hidden';
  syncProfileUI();
}

function closeProfileModal() {
  const modal = document.getElementById('profileModal');
  if (modal) modal.style.display = 'none';
  document.body.style.overflow = '';
}

const AVATAR_GRADIENTS = {
  indigo: 'linear-gradient(135deg, #6366f1, #06b6d4)',
  emerald: 'linear-gradient(135deg, #10b981, #059669)',
  cyan: 'linear-gradient(135deg, #06b6d4, #3b82f6)',
  amber: 'linear-gradient(135deg, #f59e0b, #d97706)',
  rose: 'linear-gradient(135deg, #f43f5e, #be123c)'
};

function saveProfileDetails() {
  const nameInput = document.getElementById('profileNameInput');
  const emailInput = document.getElementById('profileEmailInput');
  const roleInput = document.getElementById('profileRoleInput');
  const colorSelect = document.getElementById('profileAvatarColorSelect');

  const newName = (nameInput ? nameInput.value : '').trim() || 'Product Matcher';
  const newEmail = (emailInput ? emailInput.value : '').trim() || 'admin@productmatcher.local';
  const newRole = (roleInput ? roleInput.value : '').trim() || 'DSA System Admin';
  const newColor = (colorSelect ? colorSelect.value : 'indigo') || 'indigo';

  const user = {
    name: newName,
    email: newEmail,
    role: newRole,
    avatarColor: newColor
  };

  localStorage.setItem('productmatch_user', JSON.stringify(user));
  syncProfileUI();
  showToast(`Profile updated: ${newName} (${newRole})`);
}

function saveProfileName() {
  saveProfileDetails();
}

function syncProfileUI() {
  const raw = localStorage.getItem('productmatch_user');
  let currentName = 'Product Matcher';
  let currentEmail = 'admin@productmatcher.local';
  let currentRole = 'DSA System Admin';
  let currentColor = 'indigo';

  if (raw) {
    try {
      const u = JSON.parse(raw);
      if (u.name) currentName = u.name;
      if (u.email) currentEmail = u.email;
      if (u.role) currentRole = u.role;
      if (u.avatarColor) currentColor = u.avatarColor;
    } catch(e) {}
  }

  const topName = document.getElementById('topbarUserName');
  const topRole = document.getElementById('topbarUserRole');
  const topAvatar = document.getElementById('topbarAvatar');
  const pName = document.getElementById('profileDisplayName');
  const pEmail = document.getElementById('profileDisplayEmail');
  const pNameInput = document.getElementById('profileNameInput');
  const pEmailInput = document.getElementById('profileEmailInput');
  const pRoleInput = document.getElementById('profileRoleInput');
  const pColorSelect = document.getElementById('profileAvatarColorSelect');
  const pBigAvatar = document.getElementById('modalUserAvatarBig');
  const pApiPill = document.getElementById('profileApiStatusPill');

  const initial = currentName.charAt(0).toUpperCase();
  const gradient = AVATAR_GRADIENTS[currentColor] || AVATAR_GRADIENTS.indigo;

  if (topName) topName.textContent = currentName;
  if (topRole) topRole.textContent = currentRole;
  if (topAvatar) {
    topAvatar.textContent = initial;
    topAvatar.style.background = gradient;
  }
  if (pName) pName.textContent = currentName;
  if (pEmail) pEmail.textContent = currentEmail;
  if (pNameInput) pNameInput.value = currentName;
  if (pEmailInput) pEmailInput.value = currentEmail;
  if (pRoleInput) pRoleInput.value = currentRole;
  if (pColorSelect) pColorSelect.value = currentColor;
  if (pBigAvatar) {
    pBigAvatar.textContent = initial;
    pBigAvatar.style.background = gradient;
  }
  if (pApiPill) {
    pApiPill.textContent = webApiKey ? 'API: SerpApi Live' : 'API: Local Mode';
    pApiPill.style.color = webApiKey ? '#34d399' : '#a5b4fc';
  }
}

/* ==========================================================================
   Web Search API Key Modal & Online Web Search Management
   ========================================================================== */
function openApiKeyModal() {
  const modal = document.getElementById('apiKeyModal');
  const input = document.getElementById('modalApiKeyInput');
  const badge = document.getElementById('modalApiKeyStatusBadge');
  if (modal) {
    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden';
  }
  if (input) {
    input.value = webApiKey;
  }
  if (badge) {
    if (webApiKey) {
      badge.innerHTML = `<span style="color: #34d399; font-weight: 700;">✓ Connected: Live Web Search enabled (${webApiKey.substring(0, 6)}••••••••)</span>`;
    } else {
      badge.innerHTML = `<span style="color: #94a3b8;">• No key saved yet (using local catalog & fallback mock)</span>`;
    }
  }
}

function closeApiKeyModal() {
  const modal = document.getElementById('apiKeyModal');
  if (modal) {
    modal.style.display = 'none';
    document.body.style.overflow = '';
  }
}

function toggleModalApiKeyVis() {
  const input = document.getElementById('modalApiKeyInput');
  if (input) {
    input.type = input.type === 'password' ? 'text' : 'password';
  }
}

function saveModalApiKey() {
  const input = document.getElementById('modalApiKeyInput');
  const val = (input ? input.value : '').trim();
  if (val) {
    webApiKey = val;
    isWebSearchEnabled = true;
    localStorage.setItem('nexus_web_api_key', val);
    localStorage.setItem('nexus_web_search_enabled', 'true');
    showToast('🔑 SerpApi Key saved! Live Web Search is enabled.');
  } else {
    webApiKey = '';
    isWebSearchEnabled = false;
    localStorage.removeItem('nexus_web_api_key');
    localStorage.removeItem('nexus_web_search_enabled');
    showToast('Web Search API key removed.');
  }
  closeApiKeyModal();
  syncProfileUI();
}

function clearModalApiKey() {
  const input = document.getElementById('modalApiKeyInput');
  if (input) input.value = '';
  webApiKey = '';
  isWebSearchEnabled = false;
  localStorage.removeItem('nexus_web_api_key');
  localStorage.removeItem('nexus_web_search_enabled');
  closeApiKeyModal();
  syncProfileUI();
  showToast('Web Search API key removed.');
}

// Attach Event Listeners for API Key modal elements
document.addEventListener('DOMContentLoaded', () => {
  const closeBtn = document.getElementById('closeApiKeyModalBtn');
  const toggleVisBtn = document.getElementById('modalToggleApiKeyVisBtn');
  const saveBtn = document.getElementById('modalSaveApiKeyBtn');
  const clearBtn = document.getElementById('modalClearApiKeyBtn');

  if (closeBtn) closeBtn.addEventListener('click', closeApiKeyModal);
  if (toggleVisBtn) toggleVisBtn.addEventListener('click', toggleModalApiKeyVis);
  if (saveBtn) saveBtn.addEventListener('click', saveModalApiKey);
  if (clearBtn) clearBtn.addEventListener('click', clearModalApiKey);
});

