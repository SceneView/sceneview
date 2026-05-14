import React, { useState, useCallback } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  View,
  Switch,
  TouchableOpacity,
  FlatList,
  TextInput,
  ActivityIndicator,
  ScrollView,
  Alert,
  StatusBar,
  Platform,
} from 'react-native';
import {
  SceneView,
  ARSceneView,
  type ModelNode,
  type GeometryNode,
  type LightNode,
} from '@sceneview-sdk/react-native';
import { UpdateChecker } from './UpdateChecker';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface SketchfabResult {
  uid: string;
  name: string;
  thumbnailUrl: string;
  user: { displayName: string };
}

type TabId = 'search' | 'geometry' | 'lights' | 'ar';

interface PlaygroundShape {
  id: string;
  type: GeometryNode['type'];
  color: string;
  position: [number, number, number];
  size: [number, number, number];
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const VERSION = '4.3.1';

const ENVIRONMENT = 'environments/studio_small.hdr';

const TABS: { id: TabId; label: string; icon: string }[] = [
  { id: 'search', label: 'Search', icon: 'Q' },
  { id: 'geometry', label: 'Geometry', icon: 'G' },
  { id: 'lights', label: 'Lights', icon: 'L' },
  { id: 'ar', label: 'AR', icon: 'A' },
];

const SHAPE_TYPES: GeometryNode['type'][] = ['cube', 'sphere', 'cylinder', 'plane'];

const PRESET_COLORS = [
  '#E53935', '#D81B60', '#8E24AA', '#5E35B1',
  '#3949AB', '#1E88E5', '#039BE5', '#00ACC1',
  '#00897B', '#43A047', '#7CB342', '#C0CA33',
  '#FDD835', '#FFB300', '#FB8C00', '#F4511E',
];

const LIGHT_TYPES: LightNode['type'][] = ['directional', 'point', 'spot'];

const LIGHT_PRESETS: { label: string; nodes: LightNode[] }[] = [
  {
    label: 'Warm Sunset',
    nodes: [
      { type: 'directional', intensity: 80000, color: '#FF8C00', direction: [-1, -1, -1] },
      { type: 'point', intensity: 50000, color: '#FFD700', position: [2, 2, 0] },
    ],
  },
  {
    label: 'Cool Studio',
    nodes: [
      { type: 'directional', intensity: 100000, color: '#E0E8FF', direction: [0, -1, -1] },
      { type: 'point', intensity: 40000, color: '#B0C4FF', position: [-2, 1, 2] },
      { type: 'point', intensity: 40000, color: '#FFE0B0', position: [2, 1, 2] },
    ],
  },
  {
    label: 'Dramatic Spot',
    nodes: [
      { type: 'spot', intensity: 200000, color: '#FFFFFF', position: [0, 3, 0], direction: [0, -1, 0] },
    ],
  },
  {
    label: 'RGB Party',
    nodes: [
      { type: 'point', intensity: 80000, color: '#FF0000', position: [-2, 1, 0] },
      { type: 'point', intensity: 80000, color: '#00FF00', position: [2, 1, 0] },
      { type: 'point', intensity: 80000, color: '#0000FF', position: [0, 1, -2] },
    ],
  },
];

// ---------------------------------------------------------------------------
// Sketchfab API
// ---------------------------------------------------------------------------

async function searchSketchfab(query: string): Promise<SketchfabResult[]> {
  const url = `https://api.sketchfab.com/v3/search?type=models&downloadable=true&q=${encodeURIComponent(query)}&count=20`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Sketchfab API error: ${response.status}`);
  const data = await response.json();
  return (data.results || []).map((r: any) => ({
    uid: r.uid,
    name: r.name,
    thumbnailUrl: r.thumbnails?.images?.[0]?.url || '',
    user: { displayName: r.user?.displayName || 'Unknown' },
  }));
}

// ---------------------------------------------------------------------------
// Helper: unique ID
// ---------------------------------------------------------------------------

let _idCounter = 0;
function uniqueId(): string {
  return `shape_${++_idCounter}_${Date.now()}`;
}

// ---------------------------------------------------------------------------
// Tab: Sketchfab Search
// ---------------------------------------------------------------------------

function SearchTab() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SketchfabResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState<ModelNode | null>(null);
  const [tapInfo, setTapInfo] = useState<string | null>(null);

  const handleSearch = useCallback(async () => {
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const data = await searchSketchfab(query.trim());
      setResults(data);
      if (data.length === 0) setError('No downloadable models found. Try another query.');
    } catch (e: any) {
      setError(e.message || 'Search failed');
    } finally {
      setLoading(false);
    }
  }, [query]);

  return (
    <View style={styles.tabContent}>
      {/* Search bar */}
      <View style={styles.searchBar}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search Sketchfab models..."
          placeholderTextColor="#6B7280"
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={handleSearch}
          returnKeyType="search"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity
          style={[styles.searchButton, loading && styles.searchButtonDisabled]}
          onPress={handleSearch}
          disabled={loading}
          activeOpacity={0.7}
        >
          {loading ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={styles.searchButtonText}>Search</Text>
          )}
        </TouchableOpacity>
      </View>

      {error && <Text style={styles.errorText}>{error}</Text>}

      {/* 3D viewer when a model is selected */}
      {selectedModel && (
        <View style={styles.viewerContainer}>
          <SceneView
            style={styles.scene}
            environment={ENVIRONMENT}
            modelNodes={[selectedModel]}
            cameraOrbit
            onTap={(e) => {
              const { x, y, z, nodeName } = e.nativeEvent;
              setTapInfo(
                nodeName
                  ? `Tapped: ${nodeName} at (${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)})`
                  : `Tapped at (${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)})`
              );
            }}
          />
          {tapInfo && (
            <View style={styles.tapBadge}>
              <Text style={styles.tapBadgeText}>{tapInfo}</Text>
            </View>
          )}
          <TouchableOpacity
            style={styles.closeButton}
            onPress={() => { setSelectedModel(null); setTapInfo(null); }}
          >
            <Text style={styles.closeButtonText}>Close</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Results list */}
      {!selectedModel && (
        <FlatList
          data={results}
          keyExtractor={(item) => item.uid}
          contentContainerStyle={styles.resultsList}
          ListEmptyComponent={
            !loading && !error ? (
              <View style={styles.emptyState}>
                <Text style={styles.emptyIcon}>Q</Text>
                <Text style={styles.emptyTitle}>Search Sketchfab</Text>
                <Text style={styles.emptySubtitle}>
                  Find downloadable 3D models and view them in SceneView
                </Text>
              </View>
            ) : null
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.resultCard}
              activeOpacity={0.7}
              onPress={() => {
                // Sketchfab download requires auth, so show info
                Alert.alert(
                  item.name,
                  `By ${item.user.displayName}\n\nSketchfab model downloads require authentication. Use a local GLB file path or URL for the SceneView modelNodes prop.`,
                  [{ text: 'OK' }]
                );
              }}
            >
              <View style={styles.resultInfo}>
                <Text style={styles.resultName} numberOfLines={2}>{item.name}</Text>
                <Text style={styles.resultAuthor}>by {item.user.displayName}</Text>
                <Text style={styles.resultUid}>uid: {item.uid}</Text>
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Tab: Geometry Playground
// ---------------------------------------------------------------------------

function GeometryTab() {
  const [shapes, setShapes] = useState<PlaygroundShape[]>([
    { id: 'default_cube', type: 'cube', color: '#1E88E5', position: [0, 0, -2], size: [0.8, 0.8, 0.8] },
    { id: 'default_sphere', type: 'sphere', color: '#E53935', position: [1.5, 0, -2], size: [0.8, 0.8, 0.8] },
  ]);
  const [selectedColor, setSelectedColor] = useState('#1E88E5');
  const [selectedType, setSelectedType] = useState<GeometryNode['type']>('cube');
  const [tapInfo, setTapInfo] = useState<string | null>(null);

  const addShape = useCallback(() => {
    const xOffset = (Math.random() - 0.5) * 4;
    const yOffset = (Math.random() - 0.5) * 2;
    const newShape: PlaygroundShape = {
      id: uniqueId(),
      type: selectedType,
      color: selectedColor,
      position: [xOffset, yOffset, -2.5],
      size: [0.6, 0.6, 0.6],
    };
    setShapes((prev) => [...prev, newShape]);
  }, [selectedColor, selectedType]);

  const removeLastShape = useCallback(() => {
    setShapes((prev) => prev.slice(0, -1));
  }, []);

  const clearShapes = useCallback(() => {
    setShapes([]);
  }, []);

  const geometryNodes: GeometryNode[] = shapes.map((s) => ({
    type: s.type,
    color: s.color,
    position: s.position,
    size: s.size,
  }));

  return (
    <View style={styles.tabContent}>
      {/* 3D Scene */}
      <View style={styles.viewerContainer}>
        <SceneView
          style={styles.scene}
          environment={ENVIRONMENT}
          geometryNodes={geometryNodes}
          cameraOrbit
          onTap={(e) => {
            const { x, y, z, nodeName } = e.nativeEvent;
            setTapInfo(
              `Tap: (${x.toFixed(1)}, ${y.toFixed(1)}, ${z.toFixed(1)})${nodeName ? ` [${nodeName}]` : ''}`
            );
          }}
        />
        {tapInfo && (
          <View style={styles.tapBadge}>
            <Text style={styles.tapBadgeText}>{tapInfo}</Text>
          </View>
        )}
        <View style={styles.shapeCountBadge}>
          <Text style={styles.shapeCountText}>{shapes.length} shapes</Text>
        </View>
      </View>

      {/* Controls */}
      <ScrollView style={styles.controls} contentContainerStyle={styles.controlsContent}>
        {/* Shape type selector */}
        <Text style={styles.controlLabel}>Shape Type</Text>
        <View style={styles.chipRow}>
          {SHAPE_TYPES.map((type) => (
            <TouchableOpacity
              key={type}
              style={[styles.typeChip, selectedType === type && styles.typeChipSelected]}
              onPress={() => setSelectedType(type)}
              activeOpacity={0.7}
            >
              <Text style={[styles.typeChipText, selectedType === type && styles.typeChipTextSelected]}>
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Color picker */}
        <Text style={styles.controlLabel}>Color</Text>
        <View style={styles.colorGrid}>
          {PRESET_COLORS.map((color) => (
            <TouchableOpacity
              key={color}
              style={[
                styles.colorSwatch,
                { backgroundColor: color },
                selectedColor === color && styles.colorSwatchSelected,
              ]}
              onPress={() => setSelectedColor(color)}
              activeOpacity={0.7}
            />
          ))}
        </View>

        {/* Action buttons */}
        <View style={styles.actionRow}>
          <TouchableOpacity style={styles.actionButton} onPress={addShape} activeOpacity={0.7}>
            <Text style={styles.actionButtonText}>+ Add Shape</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonSecondary]}
            onPress={removeLastShape}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonSecondaryText}>- Remove</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonDanger]}
            onPress={clearShapes}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonDangerText}>Clear</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Tab: Lights
// ---------------------------------------------------------------------------

function LightsTab() {
  const [activeLights, setActiveLights] = useState<LightNode[]>(LIGHT_PRESETS[0].nodes);
  const [activePreset, setActivePreset] = useState(0);

  // Show geometry shapes so we can see the lighting effect
  const demoGeometry: GeometryNode[] = [
    { type: 'sphere', color: '#CCCCCC', position: [0, 0, -2], size: [1, 1, 1] },
    { type: 'cube', color: '#CCCCCC', position: [-1.8, 0, -2.5], size: [0.7, 0.7, 0.7] },
    { type: 'cylinder', color: '#CCCCCC', position: [1.8, 0, -2.5], size: [0.6, 1, 0.6] },
    { type: 'plane', color: '#888888', position: [0, -0.8, -2], size: [6, 6, 1] },
  ];

  return (
    <View style={styles.tabContent}>
      {/* 3D Scene */}
      <View style={styles.viewerContainer}>
        <SceneView
          style={styles.scene}
          environment={ENVIRONMENT}
          geometryNodes={demoGeometry}
          lightNodes={activeLights}
          cameraOrbit
        />
        <View style={styles.lightInfoBadge}>
          <Text style={styles.lightInfoText}>
            {activeLights.length} light{activeLights.length !== 1 ? 's' : ''} active
          </Text>
        </View>
      </View>

      {/* Presets */}
      <ScrollView style={styles.controls} contentContainerStyle={styles.controlsContent}>
        <Text style={styles.controlLabel}>Light Presets</Text>
        {LIGHT_PRESETS.map((preset, index) => (
          <TouchableOpacity
            key={preset.label}
            style={[styles.presetCard, activePreset === index && styles.presetCardActive]}
            onPress={() => {
              setActiveLights(preset.nodes);
              setActivePreset(index);
            }}
            activeOpacity={0.7}
          >
            <Text style={[styles.presetLabel, activePreset === index && styles.presetLabelActive]}>
              {preset.label}
            </Text>
            <Text style={styles.presetDetail}>
              {preset.nodes.map((n) => `${n.type}(${n.color})`).join(' + ')}
            </Text>
          </TouchableOpacity>
        ))}

        <Text style={[styles.controlLabel, { marginTop: 16 }]}>Custom Light</Text>
        <View style={styles.chipRow}>
          {LIGHT_TYPES.map((type) => (
            <TouchableOpacity
              key={type}
              style={styles.typeChip}
              onPress={() => {
                const newLight: LightNode = {
                  type,
                  intensity: type === 'spot' ? 200000 : 100000,
                  color: PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)],
                  position: [
                    (Math.random() - 0.5) * 4,
                    1 + Math.random() * 2,
                    (Math.random() - 0.5) * 4,
                  ],
                  direction: type !== 'point' ? [0, -1, 0] : undefined,
                };
                setActiveLights((prev) => [...prev, newLight]);
                setActivePreset(-1);
              }}
              activeOpacity={0.7}
            >
              <Text style={styles.typeChipText}>+ {type}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.actionRow}>
          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonDanger]}
            onPress={() => { setActiveLights([]); setActivePreset(-1); }}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonDangerText}>Clear All Lights</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Tab: AR Mode
// ---------------------------------------------------------------------------

function ARTab() {
  const [planeDetection, setPlaneDetection] = useState(true);
  const [depthOcclusion, setDepthOcclusion] = useState(false);
  const [instantPlacement, setInstantPlacement] = useState(false);
  const [detectedPlanes, setDetectedPlanes] = useState(0);
  const [tapInfo, setTapInfo] = useState<string | null>(null);

  const arGeometry: GeometryNode[] = [
    { type: 'cube', color: '#1E88E5', position: [0, 0, -1], size: [0.2, 0.2, 0.2] },
    { type: 'sphere', color: '#E53935', position: [0.3, 0.1, -1], size: [0.15, 0.15, 0.15] },
  ];

  const arLights: LightNode[] = [
    { type: 'directional', intensity: 100000, color: '#FFFFFF', direction: [0, -1, -1] },
  ];

  return (
    <View style={styles.tabContent}>
      {/* AR Scene */}
      <View style={styles.viewerContainer}>
        <ARSceneView
          style={styles.scene}
          planeDetection={planeDetection}
          depthOcclusion={depthOcclusion}
          instantPlacement={instantPlacement}
          geometryNodes={arGeometry}
          lightNodes={arLights}
          onTap={(e) => {
            const { x, y, z, nodeName } = e.nativeEvent;
            setTapInfo(
              nodeName
                ? `Tapped: ${nodeName} at (${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)})`
                : `Tapped at (${x.toFixed(2)}, ${y.toFixed(2)}, ${z.toFixed(2)})`
            );
          }}
          onPlaneDetected={(e) => {
            setDetectedPlanes((prev) => prev + 1);
          }}
        />
        {tapInfo && (
          <View style={styles.tapBadge}>
            <Text style={styles.tapBadgeText}>{tapInfo}</Text>
          </View>
        )}
        <View style={styles.arStatusBadge}>
          <Text style={styles.arStatusText}>
            {detectedPlanes} plane{detectedPlanes !== 1 ? 's' : ''} detected
          </Text>
        </View>
      </View>

      {/* AR Controls */}
      <ScrollView style={styles.controls} contentContainerStyle={styles.controlsContent}>
        <Text style={styles.controlLabel}>AR Features</Text>

        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Plane Detection</Text>
          <Switch
            value={planeDetection}
            onValueChange={setPlaneDetection}
            trackColor={{ false: '#3A3F4B', true: '#1E88E5' }}
            thumbColor="#fff"
          />
        </View>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Depth Occlusion</Text>
          <Switch
            value={depthOcclusion}
            onValueChange={setDepthOcclusion}
            trackColor={{ false: '#3A3F4B', true: '#1E88E5' }}
            thumbColor="#fff"
          />
        </View>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Instant Placement</Text>
          <Switch
            value={instantPlacement}
            onValueChange={setInstantPlacement}
            trackColor={{ false: '#3A3F4B', true: '#1E88E5' }}
            thumbColor="#fff"
          />
        </View>

        <View style={styles.arInfoCard}>
          <Text style={styles.arInfoTitle}>AR Bridge Features</Text>
          <Text style={styles.arInfoBody}>
            This tab demonstrates the full AR bridge:{'\n'}
            {'\u2022'} Plane detection (horizontal + vertical){'\n'}
            {'\u2022'} Depth occlusion (ARCore/LiDAR){'\n'}
            {'\u2022'} Instant placement{'\n'}
            {'\u2022'} Geometry nodes in AR{'\n'}
            {'\u2022'} Light nodes in AR{'\n'}
            {'\u2022'} Tap events with world coordinates
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Main App
// ---------------------------------------------------------------------------

export default function App() {
  const [activeTab, setActiveTab] = useState<TabId>('geometry');

  const renderTab = () => {
    switch (activeTab) {
      case 'search': return <SearchTab />;
      case 'geometry': return <GeometryTab />;
      case 'lights': return <LightsTab />;
      case 'ar': return <ARTab />;
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#0F1218" />

      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>SceneView</Text>
          <Text style={styles.subtitle}>React Native Demo</Text>
        </View>
        <View style={styles.versionBadge}>
          <Text style={styles.versionText}>v{VERSION}</Text>
        </View>
      </View>

      {/* Tab content */}
      <View style={styles.tabContainer}>
        {renderTab()}
      </View>

      {/* Bottom tab bar */}
      <View style={styles.tabBar}>
        {TABS.map((tab) => (
          <TouchableOpacity
            key={tab.id}
            style={[styles.tabItem, activeTab === tab.id && styles.tabItemActive]}
            onPress={() => setActiveTab(tab.id)}
            activeOpacity={0.7}
          >
            <View style={[styles.tabIconContainer, activeTab === tab.id && styles.tabIconContainerActive]}>
              <Text style={[styles.tabIcon, activeTab === tab.id && styles.tabIconActive]}>
                {tab.icon}
              </Text>
            </View>
            <Text style={[styles.tabLabel, activeTab === tab.id && styles.tabLabelActive]}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Auto-update banner — overlays the top of the screen when a newer
          build is on the store. Hooks `AppState` internally so a resume
          re-runs the check; no-op on dev / unsupported platforms. */}
      <UpdateChecker />
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F1218',
  },

  // Header
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#1E2430',
  },
  title: {
    color: '#FFFFFF',
    fontSize: 24,
    fontWeight: '800',
    letterSpacing: -0.5,
  },
  subtitle: {
    color: '#6B7280',
    fontSize: 13,
    fontWeight: '500',
    marginTop: 1,
  },
  versionBadge: {
    backgroundColor: '#1A2332',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#1E88E5',
  },
  versionText: {
    color: '#1E88E5',
    fontSize: 13,
    fontWeight: '700',
  },

  // Tab bar
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#0F1218',
    borderTopWidth: 1,
    borderTopColor: '#1E2430',
    paddingBottom: Platform.OS === 'ios' ? 20 : 8,
    paddingTop: 8,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 4,
  },
  tabItemActive: {},
  tabIconContainer: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#1A1F28',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  tabIconContainerActive: {
    backgroundColor: '#1A2332',
  },
  tabIcon: {
    color: '#6B7280',
    fontSize: 16,
    fontWeight: '800',
  },
  tabIconActive: {
    color: '#1E88E5',
  },
  tabLabel: {
    color: '#6B7280',
    fontSize: 11,
    fontWeight: '600',
  },
  tabLabelActive: {
    color: '#1E88E5',
  },

  // Tab content
  tabContainer: {
    flex: 1,
  },
  tabContent: {
    flex: 1,
  },

  // Scene viewer
  viewerContainer: {
    flex: 1,
    minHeight: 260,
  },
  scene: {
    flex: 1,
  },

  // Search
  searchBar: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 10,
  },
  searchInput: {
    flex: 1,
    backgroundColor: '#1A1F28',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#FFFFFF',
    fontSize: 15,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  searchButton: {
    backgroundColor: '#1E88E5',
    borderRadius: 12,
    paddingHorizontal: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchButtonDisabled: {
    opacity: 0.6,
  },
  searchButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  errorText: {
    color: '#EF4444',
    fontSize: 14,
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  resultsList: {
    paddingHorizontal: 16,
    paddingBottom: 16,
  },
  resultCard: {
    backgroundColor: '#1A1F28',
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  resultInfo: {},
  resultName: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  resultAuthor: {
    color: '#9CA3AF',
    fontSize: 13,
    marginBottom: 2,
  },
  resultUid: {
    color: '#4B5563',
    fontSize: 11,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyIcon: {
    color: '#2A3040',
    fontSize: 48,
    fontWeight: '800',
    marginBottom: 16,
  },
  emptyTitle: {
    color: '#9CA3AF',
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
  },
  emptySubtitle: {
    color: '#4B5563',
    fontSize: 14,
    textAlign: 'center',
    paddingHorizontal: 40,
  },

  // Controls
  controls: {
    maxHeight: 280,
    borderTopWidth: 1,
    borderTopColor: '#1E2430',
  },
  controlsContent: {
    padding: 16,
  },
  controlLabel: {
    color: '#9CA3AF',
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 10,
  },

  // Shape type chips
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 16,
  },
  typeChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
    backgroundColor: '#1A1F28',
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  typeChipSelected: {
    backgroundColor: '#1A2332',
    borderColor: '#1E88E5',
  },
  typeChipText: {
    color: '#9CA3AF',
    fontSize: 14,
    fontWeight: '600',
  },
  typeChipTextSelected: {
    color: '#1E88E5',
  },

  // Color picker
  colorGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 16,
  },
  colorSwatch: {
    width: 32,
    height: 32,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  colorSwatchSelected: {
    borderColor: '#FFFFFF',
    transform: [{ scale: 1.1 }],
  },

  // Action buttons
  actionRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#1E88E5',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  actionButtonSecondary: {
    backgroundColor: '#1A1F28',
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  actionButtonSecondaryText: {
    color: '#9CA3AF',
    fontSize: 15,
    fontWeight: '700',
  },
  actionButtonDanger: {
    backgroundColor: '#1A1F28',
    borderWidth: 1,
    borderColor: '#7F1D1D',
  },
  actionButtonDangerText: {
    color: '#EF4444',
    fontSize: 15,
    fontWeight: '700',
  },

  // Badges
  tapBadge: {
    position: 'absolute',
    bottom: 12,
    left: 12,
    right: 12,
    backgroundColor: 'rgba(15, 18, 24, 0.9)',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  tapBadgeText: {
    color: '#D1D5DB',
    fontSize: 13,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  shapeCountBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: 'rgba(15, 18, 24, 0.85)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  shapeCountText: {
    color: '#9CA3AF',
    fontSize: 12,
    fontWeight: '600',
  },
  lightInfoBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: 'rgba(15, 18, 24, 0.85)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  lightInfoText: {
    color: '#FFB300',
    fontSize: 12,
    fontWeight: '600',
  },
  arStatusBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: 'rgba(15, 18, 24, 0.85)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  arStatusText: {
    color: '#43A047',
    fontSize: 12,
    fontWeight: '600',
  },

  // Close button
  closeButton: {
    position: 'absolute',
    top: 12,
    left: 12,
    backgroundColor: 'rgba(15, 18, 24, 0.9)',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  closeButtonText: {
    color: '#D1D5DB',
    fontSize: 14,
    fontWeight: '600',
  },

  // Light presets
  presetCard: {
    backgroundColor: '#1A1F28',
    borderRadius: 12,
    padding: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  presetCardActive: {
    borderColor: '#FFB300',
    backgroundColor: '#1A2020',
  },
  presetLabel: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  presetLabelActive: {
    color: '#FFB300',
  },
  presetDetail: {
    color: '#6B7280',
    fontSize: 12,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },

  // AR switches
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#1E2430',
  },
  switchLabel: {
    color: '#D1D5DB',
    fontSize: 15,
    fontWeight: '500',
  },

  // AR info card
  arInfoCard: {
    backgroundColor: '#1A1F28',
    borderRadius: 12,
    padding: 16,
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  arInfoTitle: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 8,
  },
  arInfoBody: {
    color: '#9CA3AF',
    fontSize: 14,
    lineHeight: 22,
  },
});
