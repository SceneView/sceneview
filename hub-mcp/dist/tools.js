/**
 * Static tool registry for the hub-mcp lite package.
 *
 * Contains the full catalogue of 78 tools across 11 libraries, with
 * their MCP tool definitions (name, description, inputSchema) and
 * tier classification (free vs pro).
 *
 * Free tools (20) execute locally as stubs in lite mode. Pro tools (58)
 * are forwarded to the hosted gateway when an API key is set.
 *
 * The tool definitions here are a snapshot of hub-gateway/src/libraries/*.ts
 * and mcp/packages/{automotive,healthcare}/src/tools.ts. They are
 * intentionally hardcoded (no imports from those modules) so this
 * package stays zero-dep beyond the MCP SDK.
 */
// ── Free tool whitelist (mirrors hub-gateway/src/mcp/access.ts) ─────────────
export const FREE_TOOLS = new Set([
    // architecture
    "architecture__generate_3d_concept",
    "architecture__cost_estimate",
    "architecture__search_parcels", // bridge-API: cadastre.gouv.fr
    // ecommerce-3d
    "ecommerce3d__list_categories",
    // education
    "education__build_quiz",
    "education__search_papers", // bridge-API: OpenAlex
    // finance (was @thomasgorisse/finance-mcp, now personal-finance-mcp)
    "finance__compound_interest",
    "finance__get_stock_quote", // bridge-API: market data
    "finance__get_exchange_rates", // bridge-API: ECB/forex
    // french-admin
    "french_admin__list_democraties",
    "french_admin__rechercher_entreprise", // bridge-API: api.gouv.fr
    "french_admin__valider_adresse", // bridge-API: api-adresse.data.gouv.fr
    // health-fitness
    "health_fitness__exercise_form_cues",
    "health_fitness__search_nutrition", // bridge-API: nutritionix/USDA
    // legal-docs
    "legal_docs__list_templates",
    "legal_docs__generate_en_privacy_policy", // bridge: EN privacy policy generator
    // realestate
    "realestate__estimate_value",
    "realestate__search_property_transactions", // bridge-API: DVF open data
    // social-media
    "social_media__suggest_hashtags",
    // automotive-3d (unprefixed — parity with stdio package)
    "list_car_models",
    "validate_automotive_code",
    // healthcare-3d (unprefixed — parity with stdio package)
    "list_medical_models",
    // Note: validate_medical_code is also free in the gateway
]);
export function isFreeTool(name) {
    return FREE_TOOLS.has(name);
}
export function isProTool(name) {
    return !FREE_TOOLS.has(name);
}
export function getToolTier(name) {
    return FREE_TOOLS.has(name) ? "free" : "pro";
}
// ── Tool definitions ────────────────────────────────────────────────────────
//
// Organised by library. Each library section is labelled with the upstream
// package name and tool count.
export const TOOL_DEFINITIONS = [
    // ── architecture-mcp (14 tools: 3 free, 11 pro) ──────────────────────────
    {
        name: "architecture__generate_3d_concept",
        description: "Generate a 3D architectural concept from a text description. Returns style-specific materials, passive design strategies, massing recommendations, and color palette.",
        inputSchema: { type: "object", properties: { description: { type: "string" }, style: { type: "string" }, budget_range: { type: "string" }, climate: { type: "string" }, lot_size_sqm: { type: "number" } }, required: ["description"] },
    },
    {
        name: "architecture__create_floor_plan",
        description: "Create a detailed floor plan from room descriptions with dimensions. Returns room layout, SVG visualization, compliance checks, and circulation notes.",
        inputSchema: { type: "object", properties: { rooms: { type: "array" }, width_m: { type: "number" }, depth_m: { type: "number" }, stories: { type: "number" }, style: { type: "string" } }, required: ["rooms"] },
    },
    {
        name: "architecture__interior_design",
        description: "Generate interior design recommendations: mood board, color palette, furniture layout with placement rules, and budget estimate.",
        inputSchema: { type: "object", properties: { room_type: { type: "string" }, style: { type: "string" }, dimensions: { type: "object" }, budget: { type: "number" } }, required: ["room_type"] },
    },
    {
        name: "architecture__material_palette",
        description: "Generate specification-grade material suggestions for floors, walls, and ceilings with pricing, durability ratings, and sustainability scores.",
        inputSchema: { type: "object", properties: { room_type: { type: "string" }, style: { type: "string" }, budget_range: { type: "string" }, climate: { type: "string" } }, required: ["room_type"] },
    },
    {
        name: "architecture__lighting_analysis",
        description: "Analyze and design a layered lighting plan: ambient, task, and accent lighting with fixture specifications, lux calculations, and daylighting analysis.",
        inputSchema: { type: "object", properties: { room_type: { type: "string" }, dimensions: { type: "object" }, natural_light: { type: "string" }, style: { type: "string" } }, required: ["room_type"] },
    },
    {
        name: "architecture__render_walkthrough",
        description: "Generate a 3D walkthrough specification with camera path, render settings, and embeddable viewer code.",
        inputSchema: { type: "object", properties: { rooms: { type: "array" }, style: { type: "string" }, quality: { type: "string" } }, required: ["rooms"] },
    },
    {
        name: "architecture__cost_estimate",
        description: "Generate a rough cost estimate for renovation or new build: materials, labor, furniture, professional fees, contingency, and timeline.",
        inputSchema: { type: "object", properties: { project_type: { type: "string" }, area_sqm: { type: "number" }, quality_level: { type: "string" }, location: { type: "string" } }, required: ["project_type", "area_sqm"] },
    },
    {
        name: "architecture__export_specs",
        description: "Generate a technical specifications document for contractors: room-by-room finishes, electrical/plumbing points, HVAC sizing, code compliance, and required drawings.",
        inputSchema: { type: "object", properties: { rooms: { type: "array" }, building_type: { type: "string" }, code_region: { type: "string" } }, required: ["rooms"] },
    },
    {
        name: "architecture__generate_3d_walkthrough",
        description: "Generate an animated 3D walkthrough camera path through a building. Creates waypoints, camera movements, lighting, audio, and render settings.",
        inputSchema: { type: "object", properties: { rooms: { type: "array" }, duration_seconds: { type: "number" }, style: { type: "string" } }, required: ["rooms"] },
    },
    {
        name: "architecture__sustainability_analysis",
        description: "Analyze building energy efficiency, materials sustainability, water conservation, and carbon footprint. Provides certification gap analysis (LEED/BREEAM/Passive House).",
        inputSchema: { type: "object", properties: { building_type: { type: "string" }, area_sqm: { type: "number" }, climate: { type: "string" }, materials: { type: "array" }, energy_source: { type: "string" } }, required: ["building_type", "area_sqm"] },
    },
    {
        name: "architecture__search_parcels",
        description: "Search cadastral parcels by address or coordinates via cadastre.gouv.fr API. Returns parcel IDs, boundaries, and zoning info.",
        inputSchema: { type: "object", properties: { address: { type: "string" }, lat: { type: "number" }, lon: { type: "number" }, commune: { type: "string" } } },
    },
    {
        name: "architecture__get_parcel_info",
        description: "Get detailed parcel information (area, section, zoning constraints, building rights) from a cadastral parcel ID.",
        inputSchema: { type: "object", properties: { parcelId: { type: "string" }, commune: { type: "string" } }, required: ["parcelId"] },
    },
    {
        name: "architecture__search_dpe",
        description: "Search DPE (Diagnostic de Performance Energetique) energy ratings for buildings via ADEME open data. Filter by address, commune, or DPE class.",
        inputSchema: { type: "object", properties: { address: { type: "string" }, commune: { type: "string" }, dpeClass: { type: "string" }, limit: { type: "number" } } },
    },
    {
        name: "architecture__get_dpe_stats",
        description: "Get aggregated DPE energy rating statistics for a commune or department. Returns distribution by class, average consumption, and renovation recommendations.",
        inputSchema: { type: "object", properties: { commune: { type: "string" }, department: { type: "string" } } },
    },
    // ── realestate-mcp (7 tools: 2 free, 5 pro) ──────────────────────────────
    {
        name: "realestate__search_listings",
        description: "Search real estate listings by location, price range, beds/baths, and property type.",
        inputSchema: { type: "object", properties: { location: { type: "string" }, minPrice: { type: "number" }, maxPrice: { type: "number" }, minBeds: { type: "number" }, propertyType: { type: "string" }, limit: { type: "number" } }, required: ["location"] },
    },
    {
        name: "realestate__get_listing",
        description: "Retrieve full details for a single listing by id, including price history, disclosures, and HOA data.",
        inputSchema: { type: "object", properties: { listingId: { type: "string" } }, required: ["listingId"] },
    },
    {
        name: "realestate__estimate_value",
        description: "Automated valuation model (AVM) estimate for a property address, with confidence interval and comparables.",
        inputSchema: { type: "object", properties: { address: { type: "string" } }, required: ["address"] },
    },
    {
        name: "realestate__staging_assets",
        description: "List 3D staging assets (furniture, fixtures, decor) compatible with SceneView's AR preview.",
        inputSchema: { type: "object", properties: { roomType: { type: "string" }, style: { type: "string" } } },
    },
    {
        name: "realestate__search_property_transactions",
        description: "Search real property transactions (DVF open data) by commune, date range, and property type. Returns sale prices, dates, and property details.",
        inputSchema: { type: "object", properties: { commune: { type: "string" }, codePostal: { type: "string" }, dateMin: { type: "string" }, dateMax: { type: "string" }, propertyType: { type: "string" }, limit: { type: "number" } }, required: ["commune"] },
    },
    {
        name: "realestate__get_price_per_sqm",
        description: "Get median price per square meter for a commune or neighborhood using DVF transaction data. Returns stats by property type and trend over time.",
        inputSchema: { type: "object", properties: { commune: { type: "string" }, codePostal: { type: "string" }, propertyType: { type: "string" }, period: { type: "string" } }, required: ["commune"] },
    },
    {
        name: "realestate__compare_neighborhoods",
        description: "Compare real estate metrics (price/sqm, transaction volume, price trends) across multiple communes or neighborhoods using DVF data.",
        inputSchema: { type: "object", properties: { communes: { type: "array" }, propertyType: { type: "string" }, period: { type: "string" } }, required: ["communes"] },
    },
    // ── french-admin-mcp (9 tools: 3 free, 6 pro) ────────────────────────────
    {
        name: "french_admin__list_democraties",
        description: "List supported French administrative demarches (impots, caf, ameli, pole_emploi, urssaf, service_public).",
        inputSchema: { type: "object", properties: {} },
    },
    {
        name: "french_admin__search_form",
        description: "Search for an official French administrative form (Cerfa) by keyword and get the direct PDF link.",
        inputSchema: { type: "object", properties: { query: { type: "string" }, administration: { type: "string" } }, required: ["query"] },
    },
    {
        name: "french_admin__calculate_impots",
        description: "Estimate French income tax from taxable income, parts, and year. Returns net due and marginal rate.",
        inputSchema: { type: "object", properties: { revenuNet: { type: "number" }, parts: { type: "number" }, year: { type: "number" } }, required: ["revenuNet", "parts"] },
    },
    {
        name: "french_admin__caf_eligibility",
        description: "Check eligibility for CAF benefits (APL, prime d'activite, AAH, RSA) given household situation.",
        inputSchema: { type: "object", properties: { situation: { type: "string" }, nbEnfants: { type: "number" }, revenuMensuel: { type: "number" }, loyer: { type: "number" }, codePostal: { type: "string" } }, required: ["situation", "revenuMensuel"] },
    },
    {
        name: "french_admin__rechercher_entreprise",
        description: "Search French companies by name, SIREN, or SIRET via api.gouv.fr (API Entreprise). Returns registration info, legal form, NAF code, and address.",
        inputSchema: { type: "object", properties: { query: { type: "string" }, codePostal: { type: "string" }, departement: { type: "string" }, limit: { type: "number" } }, required: ["query"] },
    },
    {
        name: "french_admin__details_entreprise",
        description: "Get detailed company info (dirigeants, effectif, chiffre d'affaires, beneficiaires effectifs) from a SIREN or SIRET via api.gouv.fr.",
        inputSchema: { type: "object", properties: { siren: { type: "string" }, siret: { type: "string" } } },
    },
    {
        name: "french_admin__valider_adresse",
        description: "Validate and normalize a French postal address via api-adresse.data.gouv.fr (BAN). Returns standardized address, coordinates, and confidence score.",
        inputSchema: { type: "object", properties: { address: { type: "string" }, codePostal: { type: "string" }, city: { type: "string" } }, required: ["address"] },
    },
    {
        name: "french_admin__geocodage_inverse",
        description: "Reverse geocode GPS coordinates to a French postal address via api-adresse.data.gouv.fr (BAN). Returns nearest address, commune, and code postal.",
        inputSchema: { type: "object", properties: { lat: { type: "number" }, lon: { type: "number" } }, required: ["lat", "lon"] },
    },
    {
        name: "french_admin__info_commune",
        description: "Get detailed commune info (population, code INSEE, arrondissement, EPCI, departement, region) via geo.api.gouv.fr.",
        inputSchema: { type: "object", properties: { nom: { type: "string" }, codePostal: { type: "string" }, codeInsee: { type: "string" } } },
    },
    // ── ecommerce-3d-mcp (3 tools: 1 free, 2 pro) ───────────────────────────
    {
        name: "ecommerce3d__list_categories",
        description: "List product categories supported by the ecommerce-3d catalogue (furniture, apparel, eyewear, jewelry, footwear, home_decor).",
        inputSchema: { type: "object", properties: {} },
    },
    {
        name: "ecommerce3d__search_products",
        description: "Search the ecommerce-3d catalogue and return 3D models (glTF/USDZ) ready for SceneView rendering or AR try-on.",
        inputSchema: { type: "object", properties: { category: { type: "string" }, query: { type: "string" }, maxPriceUSD: { type: "number" }, hasAR: { type: "boolean" }, limit: { type: "number" } } },
    },
    {
        name: "ecommerce3d__configurator_options",
        description: "Return the configurable options (material, color, size) for a given product SKU so an AI agent can build a configurator UI.",
        inputSchema: { type: "object", properties: { sku: { type: "string" } }, required: ["sku"] },
    },
    // ── legal-docs-mcp (6 tools: 2 free, 4 pro) ─────────────────────────────
    {
        name: "legal_docs__list_templates",
        description: "List contract templates (NDA, employment, freelance, SaaS ToS, privacy policy, licence agreement). Informational, not legal counsel.",
        inputSchema: { type: "object", properties: { jurisdiction: { type: "string" } } },
    },
    {
        name: "legal_docs__generate_clause",
        description: "Generate a contract clause from a structured intent (confidentiality, termination, IP assignment, arbitration, warranty). Informational only.",
        inputSchema: { type: "object", properties: { clauseType: { type: "string" }, tone: { type: "string" }, jurisdiction: { type: "string" } }, required: ["clauseType"] },
    },
    {
        name: "legal_docs__review_nda",
        description: "Review an NDA draft and return a structured checklist of missing elements. Informational only.",
        inputSchema: { type: "object", properties: { draftMarkdown: { type: "string" } }, required: ["draftMarkdown"] },
    },
    {
        name: "legal_docs__generate_en_privacy_policy",
        description: "Generate an English-language privacy policy from structured inputs (company name, data collected, third parties, retention). GDPR + CCPA compliant template. Informational only.",
        inputSchema: { type: "object", properties: { companyName: { type: "string" }, websiteUrl: { type: "string" }, dataCollected: { type: "array" }, thirdParties: { type: "array" }, retentionDays: { type: "number" }, jurisdiction: { type: "string" } }, required: ["companyName"] },
    },
    {
        name: "legal_docs__generate_en_tos",
        description: "Generate English-language Terms of Service from structured inputs (service description, liability limits, governing law). Informational only.",
        inputSchema: { type: "object", properties: { companyName: { type: "string" }, serviceDescription: { type: "string" }, governingLaw: { type: "string" }, liabilityLimit: { type: "string" }, arbitration: { type: "boolean" } }, required: ["companyName", "serviceDescription"] },
    },
    {
        name: "legal_docs__generate_dmca_notice",
        description: "Generate a DMCA takedown notice from structured inputs (copyrighted work, infringing URL, contact info). Informational only.",
        inputSchema: { type: "object", properties: { copyrightOwner: { type: "string" }, copyrightedWork: { type: "string" }, infringingUrl: { type: "string" }, contactEmail: { type: "string" } }, required: ["copyrightOwner", "copyrightedWork", "infringingUrl"] },
    },
    // ── finance-mcp (6 tools: 3 free, 3 pro) ─────────────────────────────────
    {
        name: "finance__market_quote",
        description: "Look up the latest market quote for a ticker symbol across equities, ETFs, and crypto. Read-only.",
        inputSchema: { type: "object", properties: { symbol: { type: "string" }, exchange: { type: "string" } }, required: ["symbol"] },
    },
    {
        name: "finance__portfolio_summary",
        description: "Summarize a portfolio from a list of holdings (symbol + quantity + cost basis). Returns mark-to-market value, unrealized P/L, allocation. NEVER places orders.",
        inputSchema: { type: "object", properties: { holdings: { type: "array" }, currency: { type: "string" } }, required: ["holdings"] },
    },
    {
        name: "finance__compound_interest",
        description: "Compute the future value of a savings plan with periodic contributions and a target annual rate.",
        inputSchema: { type: "object", properties: { principal: { type: "number" }, monthlyContribution: { type: "number" }, annualRatePercent: { type: "number" }, years: { type: "number" } }, required: ["principal", "annualRatePercent", "years"] },
    },
    {
        name: "finance__get_stock_quote",
        description: "Get a real-time stock quote (price, change, volume, market cap) for a ticker symbol via a market data API. Read-only, NEVER places orders.",
        inputSchema: { type: "object", properties: { symbol: { type: "string" }, exchange: { type: "string" } }, required: ["symbol"] },
    },
    {
        name: "finance__get_crypto_price",
        description: "Get current cryptocurrency price, 24h change, and market cap for a coin ID (bitcoin, ethereum, etc.) via CoinGecko API. Read-only.",
        inputSchema: { type: "object", properties: { coinId: { type: "string" }, vsCurrency: { type: "string" } }, required: ["coinId"] },
    },
    {
        name: "finance__get_exchange_rates",
        description: "Get current exchange rates for a base currency against one or more target currencies via ECB/forex data. Read-only.",
        inputSchema: { type: "object", properties: { baseCurrency: { type: "string" }, targetCurrencies: { type: "array" } }, required: ["baseCurrency"] },
    },
    // ── education-mcp (6 tools: 2 free, 4 pro) ───────────────────────────────
    {
        name: "education__generate_lesson_plan",
        description: "Generate a structured lesson plan for a subject, grade level, and duration. Returns objectives, warm-up, activities, assessment, and homework.",
        inputSchema: { type: "object", properties: { subject: { type: "string" }, gradeLevel: { type: "string" }, topic: { type: "string" }, durationMinutes: { type: "number" } }, required: ["subject", "gradeLevel", "topic"] },
    },
    {
        name: "education__build_quiz",
        description: "Build a quiz of N multiple-choice questions on a given topic with answer keys and difficulty estimates.",
        inputSchema: { type: "object", properties: { topic: { type: "string" }, numQuestions: { type: "number" }, difficulty: { type: "string" } }, required: ["topic", "numQuestions"] },
    },
    {
        name: "education__curriculum_align",
        description: "Align a lesson plan against a named standard (Common Core, IB, Cambridge, French Socle Commun) and report covered / missing strands.",
        inputSchema: { type: "object", properties: { lessonPlanMarkdown: { type: "string" }, standard: { type: "string" } }, required: ["lessonPlanMarkdown", "standard"] },
    },
    {
        name: "education__search_papers",
        description: "Search academic papers via OpenAlex API by keyword, author, or DOI. Returns title, authors, abstract, citation count, and open access URL.",
        inputSchema: { type: "object", properties: { query: { type: "string" }, author: { type: "string" }, doi: { type: "string" }, limit: { type: "number" }, sort: { type: "string" } }, required: ["query"] },
    },
    {
        name: "education__get_author_profile",
        description: "Get an academic author profile from OpenAlex (publications, h-index, citations, institutional affiliations, co-authors).",
        inputSchema: { type: "object", properties: { authorName: { type: "string" }, orcid: { type: "string" } } },
    },
    {
        name: "education__get_citation_stats",
        description: "Get citation statistics and impact metrics for a paper (DOI) or author from OpenAlex. Returns citation count, citing works, and field-normalized impact.",
        inputSchema: { type: "object", properties: { doi: { type: "string" }, authorId: { type: "string" } } },
    },
    // ── social-media-mcp (3 tools: 1 free, 2 pro) ────────────────────────────
    {
        name: "social_media__suggest_hashtags",
        description: "Suggest relevant hashtags for a post on a given platform (instagram, tiktok, x, linkedin, threads). Returns ranked hashtags with approx reach.",
        inputSchema: { type: "object", properties: { platform: { type: "string" }, topic: { type: "string" }, tone: { type: "string" }, count: { type: "number" } }, required: ["platform", "topic"] },
    },
    {
        name: "social_media__caption_variants",
        description: "Generate N caption variants for a post (short, medium, long + CTA variants) optimised for the target platform.",
        inputSchema: { type: "object", properties: { platform: { type: "string" }, brief: { type: "string" }, variants: { type: "number" }, includeCta: { type: "boolean" } }, required: ["platform", "brief"] },
    },
    {
        name: "social_media__plan_content_calendar",
        description: "Plan a 4-week content calendar for a brand given its niche, audience, and posting cadence. Does NOT publish anything.",
        inputSchema: { type: "object", properties: { niche: { type: "string" }, platforms: { type: "array" }, postsPerWeek: { type: "number" } }, required: ["niche", "platforms"] },
    },
    // ── health-fitness-mcp (8 tools: 2 free, 6 pro) ──────────────────────────
    {
        name: "health_fitness__workout_plan",
        description: "Generate a weekly workout plan for a goal (strength, hypertrophy, endurance, fat_loss). Informational, not medical advice.",
        inputSchema: { type: "object", properties: { goal: { type: "string" }, experience: { type: "string" }, daysPerWeek: { type: "number" }, equipment: { type: "array" } }, required: ["goal", "experience"] },
    },
    {
        name: "health_fitness__macro_calculator",
        description: "Estimate daily macros (protein, carbs, fat) from body stats, activity level, and a goal. Informational, not a nutrition prescription.",
        inputSchema: { type: "object", properties: { heightCm: { type: "number" }, weightKg: { type: "number" }, age: { type: "number" }, biologicalSex: { type: "string" }, activityLevel: { type: "string" }, goal: { type: "string" } }, required: ["heightCm", "weightKg", "age", "biologicalSex", "activityLevel", "goal"] },
    },
    {
        name: "health_fitness__exercise_form_cues",
        description: "Return form cues and common mistakes for an exercise by name (squat, deadlift, bench_press, pull_up, running).",
        inputSchema: { type: "object", properties: { exercise: { type: "string" } }, required: ["exercise"] },
    },
    {
        name: "health_fitness__get_strava_activities",
        description: "Fetch recent Strava activities (runs, rides, swims) with distance, duration, pace, and heart rate. Requires Strava API token. Informational only.",
        inputSchema: { type: "object", properties: { activityType: { type: "string" }, limit: { type: "number" }, after: { type: "string" } } },
    },
    {
        name: "health_fitness__get_strava_athlete_stats",
        description: "Get Strava athlete stats (YTD totals, all-time totals, recent totals for run/ride/swim). Requires Strava API token. Informational only.",
        inputSchema: { type: "object", properties: {} },
    },
    {
        name: "health_fitness__analyze_strava_trends",
        description: "Analyze training trends from Strava data: weekly volume, pace progression, heart rate zones, rest days, and overtraining risk. Informational, not medical advice.",
        inputSchema: { type: "object", properties: { period: { type: "string" }, activityType: { type: "string" } } },
    },
    {
        name: "health_fitness__search_nutrition",
        description: "Search food nutrition data (calories, macros, micronutrients) via USDA/Nutritionix API. Returns per-serving and per-100g values. Informational only.",
        inputSchema: { type: "object", properties: { query: { type: "string" }, servingSize: { type: "string" } }, required: ["query"] },
    },
    {
        name: "health_fitness__log_meal_nutrition",
        description: "Log a meal and compute total nutrition (calories, protein, carbs, fat, fiber) from a list of food items with quantities. Informational only.",
        inputSchema: { type: "object", properties: { items: { type: "array" }, mealType: { type: "string" } }, required: ["items"] },
    },
    // ── automotive-3d-mcp (9 tools: 2 free, 7 pro) ───────────────────────────
    {
        name: "get_car_configurator",
        description: "Returns a compilable Kotlin composable for a 3D car configurator using SceneView. Supports 10 body styles, color picker, material variants, camera presets, turntable, and AR mode.",
        inputSchema: { type: "object", properties: { bodyStyle: { type: "string" }, colorPicker: { type: "boolean" }, materialVariants: { type: "boolean" }, turntable: { type: "boolean" }, ar: { type: "boolean" } }, required: ["bodyStyle"] },
    },
    {
        name: "get_hud_overlay",
        description: "Returns a compilable Kotlin composable for a heads-up display overlay using SceneView ViewNode. 8 HUD elements, 6 styles, night mode, metric/imperial.",
        inputSchema: { type: "object", properties: { elements: { type: "array" }, style: { type: "string" }, nightMode: { type: "boolean" }, units: { type: "string" }, ar: { type: "boolean" } }, required: ["elements"] },
    },
    {
        name: "get_dashboard_3d",
        description: "Returns a compilable Kotlin composable for a 3D instrument cluster using SceneView. 8 gauge types, 6 themes, spring-damped animations.",
        inputSchema: { type: "object", properties: { gauges: { type: "array" }, theme: { type: "string" }, animated: { type: "boolean" }, interactive: { type: "boolean" }, ar: { type: "boolean" } }, required: ["gauges"] },
    },
    {
        name: "get_ar_showroom",
        description: "Returns a compilable Kotlin composable for an AR car showroom. Walk-around, open doors, color swap, real-world measurements, photo capture.",
        inputSchema: { type: "object", properties: { location: { type: "string" }, features: { type: "array" }, realScale: { type: "boolean" }, shadows: { type: "boolean" } }, required: ["location"] },
    },
    {
        name: "get_parts_catalog",
        description: "Returns a compilable Kotlin composable for a 3D parts catalog explorer. 10 categories, exploded-view, part selection, detail zoom, assembly animation.",
        inputSchema: { type: "object", properties: { category: { type: "string" }, features: { type: "array" }, partNumbers: { type: "boolean" }, pricing: { type: "boolean" }, ar: { type: "boolean" } }, required: ["category"] },
    },
    {
        name: "get_ev_charging_station_viewer",
        description: "Returns a compilable Kotlin composable for a 3D EV charging station with live charge overlay. 5 connectors, 4 station layouts, AR mode.",
        inputSchema: { type: "object", properties: { connector: { type: "string" }, layout: { type: "string" }, overlay: { type: "boolean" }, ar: { type: "boolean" } } },
    },
    {
        name: "get_car_paint_shader",
        description: "Returns a Filament .mat car-paint material definition (clearcoat + metallic flakes) with a Kotlin SceneView snippet. 4 finish presets.",
        inputSchema: { type: "object", properties: { baseColorHex: { type: "string" }, metallic: { type: "number" }, roughness: { type: "number" }, clearcoat: { type: "number" }, finish: { type: "string" } } },
    },
    {
        name: "list_car_models",
        description: "Lists free, openly-licensed 3D car models suitable for SceneView apps. Includes complete cars, concepts, parts, interiors, wheels, engines, and test models.",
        inputSchema: { type: "object", properties: { category: { type: "string" }, tag: { type: "string" } } },
    },
    {
        name: "validate_automotive_code",
        description: "Validates a Kotlin SceneView snippet for common automotive-app mistakes: threading violations, null-safety, deprecated APIs, unrealistic scale.",
        inputSchema: { type: "object", properties: { code: { type: "string" } }, required: ["code"] },
    },
    // ── healthcare-3d-mcp (7 tools: 2 free, 5 pro) ───────────────────────────
    {
        name: "get_anatomy_viewer",
        description: "Returns a compilable Kotlin composable for a 3D anatomy viewer. Supports skeleton, muscular, circulatory, nervous, respiratory, digestive systems. Transparency, exploded view, labels, AR.",
        inputSchema: { type: "object", properties: { system: { type: "string" }, region: { type: "string" }, transparent: { type: "boolean" }, exploded: { type: "boolean" }, labels: { type: "boolean" }, ar: { type: "boolean" } }, required: ["system"] },
    },
    {
        name: "get_molecule_viewer",
        description: "Returns a compilable Kotlin composable for a 3D molecular structure viewer. Proteins, DNA, RNA, small molecules. Ball-and-stick, space-filling, ribbon representations.",
        inputSchema: { type: "object", properties: { moleculeType: { type: "string" }, representation: { type: "string" }, pdbId: { type: "string" }, colorScheme: { type: "string" }, ar: { type: "boolean" } }, required: ["moleculeType"] },
    },
    {
        name: "get_medical_imaging",
        description: "Returns a compilable Kotlin composable for 3D medical imaging visualization (CT, MRI, PET, ultrasound, X-ray). DICOM pipeline, windowing, segmentation, AR.",
        inputSchema: { type: "object", properties: { modality: { type: "string" }, renderingMode: { type: "string" }, bodyRegion: { type: "string" }, windowing: { type: "boolean" }, segmentation: { type: "boolean" }, ar: { type: "boolean" } }, required: ["modality"] },
    },
    {
        name: "get_surgical_planning",
        description: "Returns a compilable Kotlin composable for surgical planning 3D visualization. Orthopedic, cardiac, neurosurgery, spinal. Measurement, annotation, implant preview.",
        inputSchema: { type: "object", properties: { surgeryType: { type: "string" }, features: { type: "array" }, implantModel: { type: "boolean" }, ar: { type: "boolean" } }, required: ["surgeryType"] },
    },
    {
        name: "get_dental_viewer",
        description: "Returns a compilable Kotlin composable for dental 3D scanning visualization. Full-arch, single-tooth, implant, orthodontic, crown-bridge views.",
        inputSchema: { type: "object", properties: { viewType: { type: "string" }, features: { type: "array" }, arch: { type: "string" }, showRoots: { type: "boolean" }, ar: { type: "boolean" } }, required: ["viewType"] },
    },
    {
        name: "list_medical_models",
        description: "Lists free, openly-licensed 3D medical models suitable for SceneView apps. Sources: BodyParts3D, NIH 3D Print Exchange, Sketchfab.",
        inputSchema: { type: "object", properties: { category: { type: "string" }, tag: { type: "string" } } },
    },
    {
        name: "validate_medical_code",
        description: "Validates a Kotlin SceneView snippet for common healthcare-app mistakes: threading violations, null-safety, deprecated APIs, educational framing.",
        inputSchema: { type: "object", properties: { code: { type: "string" } }, required: ["code"] },
    },
];
