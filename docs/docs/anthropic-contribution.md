# Contributing to Anthropic's Ecosystem — Strategy & Plan

**Goal**: Get SceneView recognized as the 3D rendering solution for Claude's ecosystem
through genuine, high-value contributions to Anthropic's open-source repos.

**Philosophy**: Lead with value, not self-promotion. Every contribution must genuinely
help Anthropic's developer community. SceneView gets noticed as a natural consequence.

---

## 1. Anthropic's Open-Source Landscape

### Key GitHub Organizations

| Organization | URL | Purpose |
|---|---|---|
| **anthropics** | github.com/anthropics (77 repos) | SDKs, tools, skills, cookbooks, plugins |
| **modelcontextprotocol** | github.com/modelcontextprotocol | MCP spec, SDKs, registry, reference servers |

### High-Value Repos for Contribution

| Repository | Stars | What It Is | Contribution Path |
|---|---|---|---|
| `anthropics/claude-cookbooks` | High | Jupyter notebooks, recipes for using Claude | Add 3D cookbook recipe |
| `anthropics/skills` | Medium | Agent Skills (SKILL.md folders) | Add 3D rendering skill |
| `anthropics/knowledge-work-plugins` | Medium | Cowork plugins (11 categories) | Add design/3D plugin |
| `modelcontextprotocol/servers` | Very High | Reference MCP servers | Bug fixes only (no new servers accepted) |
| `modelcontextprotocol/registry` | Medium | MCP server registry | Already listed (io.github.sceneview/mcp) |

### Repos That Do NOT Accept New Servers

The `modelcontextprotocol/servers` repo explicitly states: new server implementations
are no longer accepted. They redirect to the MCP Registry instead. Only bug fixes and
protocol feature demonstrations are welcome.

---

## 2. Contribution Opportunities (Ranked by Impact)

### Tier 1 — High Impact, High Visibility

#### A. Claude Cookbooks: "3D Model Viewer with MCP" Recipe
- **Repo**: `anthropics/claude-cookbooks`
- **What**: A Jupyter notebook showing how to use an MCP server to generate 3D scenes
- **Format**: Python notebook + companion code
- **Angle**: "Tool Use" category — demonstrate MCP tool integration for 3D rendering
- **Why it works**: Cookbooks explicitly welcome community contributions. A 3D recipe
  fills a gap nobody else has addressed. The cookbook format (runnable, educational) is
  exactly what Anthropic wants.
- **PR title**: "Add 3D model viewer recipe using MCP tool integration"
- **Effort**: 2-3 days
- **Status**: Ready to start

#### B. Knowledge Work Plugin: "3D Design / Architecture" Plugin
- **Repo**: `anthropics/knowledge-work-plugins`
- **What**: A new plugin for architects, designers, 3D artists working in Claude Cowork
- **Structure**:
  ```
  3d-design/
  ├── .claude-plugin/plugin.json
  ├── .mcp.json              # Wire to SceneView MCP
  ├── commands/
  │   ├── create-scene.md    # /3d:create-scene
  │   ├── preview-model.md   # /3d:preview-model
  │   └── ar-preview.md      # /3d:ar-preview
  └── skills/
      ├── 3d-modeling.md
      └── ar-design.md
  ```
- **Why it works**: No 3D/visualization plugin exists. Fills a genuine gap. Plugin
  contribution is simple (markdown + JSON, no code). Architecture/design is a natural
  knowledge-work use case.
- **PR title**: "Add 3D design and architecture plugin"
- **Effort**: 1-2 days
- **Status**: Ready to start

#### C. Agent Skill: "3D Scene Builder"
- **Repo**: `anthropics/skills`
- **What**: A skill that teaches Claude how to generate 3D scenes using SceneView
- **Structure**:
  ```
  3d-scene-builder/
  └── SKILL.md
  ```
  With frontmatter: `name: 3d-scene-builder`, description of when to use it, examples
  of generating Compose code for 3D scenes, AR placement, model loading.
- **Why it works**: Skills are Anthropic's newest contribution vector. Repository is
  actively maintained and accepting contributions. No 3D skill exists.
- **PR title**: "Add 3D scene builder skill for Android/iOS development"
- **Effort**: 1 day
- **Status**: Ready to start

### Tier 2 — Medium Impact, Good Visibility

#### D. MCP Registry: Ensure Quality Listing
- **Registry**: registry.modelcontextprotocol.io
- **What**: SceneView MCP is already published (io.github.sceneview/mcp v4.0.0).
  Ensure the listing has excellent metadata, description, and examples.
- **Action**: Verify server.json quality, add screenshots, ensure all tools are
  documented, add usage examples.
- **Effort**: Half day
- **Status**: Already listed, needs polish

#### E. Awesome MCP Servers Lists: Add SceneView
- **Repos**: Multiple community-curated lists
  - `appcypher/awesome-mcp-servers` (most popular)
  - `wong2/awesome-mcp-servers`
  - `TensorBlock/awesome-mcp-servers`
- **What**: Submit PR to add SceneView MCP under "3D Rendering" or "Visualization"
- **Note**: Some of these already have 3D categories (Blender, Unreal Engine, Fusion 360).
  SceneView would be the mobile/cross-platform entry.
- **Effort**: 1 hour per list
- **Status**: Ready to submit

### Tier 3 — Lower Impact, Long-Term Value

#### F. MCP Specification: 3D Rendering Protocol Extension
- **Repo**: `modelcontextprotocol/modelcontextprotocol`
- **What**: Propose a standard for 3D content in MCP responses (e.g., a `3d_model`
  content type alongside `text`, `image`, `resource`)
- **Why cautious**: Spec changes require RFC process, community consensus, and are
  slow. This is a 6-12 month play.
- **Action**: Open a Discussion (not PR) proposing the idea. Gauge interest.
- **Effort**: 1 day to write proposal, months for process
- **Status**: Future consideration

#### G. Anthropic Engineering Blog: Guest Post
- **URL**: anthropic.com/engineering
- **What**: Pitch a guest post about building MCP servers for 3D rendering
- **Reality check**: Anthropic's engineering blog features internal projects. External
  guest posts are rare. Better to get featured indirectly (via cookbook contribution
  being mentioned, or via Twitter/X engagement).
- **Action**: Build the portfolio first (cookbooks, skills, plugins), then pitch
- **Effort**: N/A for now
- **Status**: Long-term, after Tier 1 contributions land

---

## 3. Community Channels

### Where to Be Visible

| Channel | URL | How to Use |
|---|---|---|
| **Claude Discord** | discord.com/invite/6PPFFzqPDZ (~75K members) | Share MCP server, help developers, post in #showcase |
| **X/Twitter @AnthropicAI** | x.com/AnthropicAI | Tag in posts about SceneView MCP, engage with their developer content |
| **X/Twitter @claudeai** | x.com/claudeai | Tag when showing Claude + 3D demos |
| **DEV Community** | dev.to/t/anthropic | Write tutorials about Claude + 3D |
| **MCP Discord** | (check modelcontextprotocol.io for link) | Share MCP server, get feedback |
| **Anthropic Developer Forum** | support.anthropic.com | Answer questions, share expertise |

### Key People to Engage With (Respectfully)

| Person | Role | Platform |
|---|---|---|
| **@alexalbert__** | Head of Developer Relations | X/Twitter |
| **@mikeyk** | CPO (Mike Krieger, Instagram co-founder) | X/Twitter |
| **@AnthropicAI** | Official account | X/Twitter |

**Approach**: Do NOT cold-DM. Instead:
1. Build in public — post progress on X with relevant tags
2. Tag @AnthropicAI when sharing genuine demos
3. Contribute to their repos first, THEN reference the contributions
4. Engage meaningfully with their posts (not just "great work!")

---

## 4. Execution Timeline

### Week 1: Foundation
- [ ] Polish MCP Registry listing (server.json, metadata, examples)
- [ ] Submit PRs to 3 awesome-mcp-servers lists
- [ ] Join Claude Discord, introduce SceneView MCP in #showcase
- [ ] Post on X: "Built an MCP server for 3D rendering — works with Claude"

### Week 2: Cookbook Contribution
- [ ] Write Jupyter notebook: "3D Model Viewer with Claude MCP Tools"
- [ ] Test thoroughly, ensure it runs standalone
- [ ] Submit PR to `anthropics/claude-cookbooks`
- [ ] Share cookbook PR on X, tag @AnthropicAI

### Week 3: Skills + Plugin
- [ ] Write 3D Scene Builder skill (SKILL.md)
- [ ] Submit PR to `anthropics/skills`
- [ ] Write 3D Design plugin for knowledge-work-plugins
- [ ] Submit PR to `anthropics/knowledge-work-plugins`

### Week 4: Amplification
- [ ] Write DEV.to tutorial: "Adding 3D Rendering to Claude with MCP"
- [ ] Post demo video on X showing Claude generating 3D scenes
- [ ] Engage with any Anthropic responses to PRs
- [ ] Open Discussion on MCP spec about 3D content type (if PRs landed well)

### Ongoing
- [ ] Respond to PR review feedback promptly and professionally
- [ ] Keep MCP server updated with latest protocol changes
- [ ] Help other developers in Claude Discord
- [ ] Monitor Anthropic blog/X for opportunities to contribute

---

## 5. PR Descriptions (Ready to Use)

### For claude-cookbooks

```
## Add 3D Model Viewer Recipe Using MCP Tool Integration

This cookbook demonstrates how to use Claude's tool use capabilities to generate
interactive 3D scenes through an MCP server.

### What this covers
- Setting up an MCP server for 3D rendering (SceneView)
- Using Claude to generate 3D scene descriptions from natural language
- Converting Claude's output to renderable 3D content
- Practical examples: product viewer, architectural walkthrough, educational model

### Why this belongs in the cookbook
No existing recipe covers 3D/spatial content generation. As AR/VR and spatial computing
grow (Apple Vision Pro, Android XR, Meta Quest), developers increasingly need AI-assisted
3D content creation. This recipe fills that gap.

### Testing
- Tested with Claude 3.5 Sonnet and Claude 3 Opus
- All notebook cells execute successfully
- MCP server available on npm: @anthropic/sceneview-mcp
```

### For awesome-mcp-servers

```
## Add SceneView MCP Server (3D Rendering)

### Server Details
- **Name**: SceneView MCP
- **npm**: `io.github.sceneview/mcp`
- **Category**: 3D Rendering / Visualization
- **Description**: MCP server for 3D scene generation, model loading, and AR preview.
  Supports Android (Jetpack Compose), iOS (SwiftUI), and Web platforms.
- **Tools**: create_scene, load_model, get_ar_setup, render_preview, get_code_sample
- **License**: Apache 2.0
```

---

## 6. What NOT to Do

- **Do NOT spam PRs** — quality over quantity, one at a time
- **Do NOT make PRs that are just "add my project to your list"** without genuine value
- **Do NOT cold-DM Anthropic employees** asking for partnerships
- **Do NOT claim official partnership** — say "community contribution"
- **Do NOT rush** — Anthropic reviews PRs carefully, be patient
- **Do NOT self-promote in Discord** — help others first, mention SceneView naturally
- **Do NOT open issues demanding features** — propose solutions, not problems

---

## 7. Realistic Expectations

### What Success Looks Like

| Outcome | Likelihood | Timeframe |
|---|---|---|
| PRs merged into cookbooks/skills/plugins | High (if quality is good) | 2-6 weeks |
| Listed in awesome-mcp-servers lists | Very High | 1-2 weeks |
| Mentioned in Anthropic tweet/blog | Low | 3-6 months |
| Official partnership discussion | Very Low | 6-12 months |
| SceneView becomes default 3D solution | Possible long-term | 12+ months |

### The Real Path to Being Noticed

1. **Be the best MCP server in the 3D category** — nobody else is doing this well
2. **Make contributions that Anthropic employees actually use** — the cookbook recipe
   is key because it becomes part of their official docs
3. **Build community traction** — if developers love SceneView MCP, Anthropic notices
4. **Be patient and professional** — Anthropic values thoughtful contributors

The strongest signal is not a pitch deck — it is merged PRs, active users, and a
server that works flawlessly. Build that, and the recognition follows.
