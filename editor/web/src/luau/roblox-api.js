// A curated subset of the Roblox API used for autocompletion and highlighting.
// It is NOT generated from the full API dump, so it covers common classes only.
//
// Class format: name -> [superclass, "methods", "properties", "events"] (space separated lists).

export const CLASSES = {
  Instance: [null,
    "Clone Destroy ClearAllChildren FindFirstChild FindFirstChildOfClass FindFirstChildWhichIsA FindFirstAncestor FindFirstAncestorOfClass FindFirstAncestorWhichIsA FindFirstDescendant GetAttribute GetAttributes SetAttribute GetAttributeChangedSignal GetChildren GetDescendants GetFullName GetPropertyChangedSignal IsA IsAncestorOf IsDescendantOf WaitForChild AddTag RemoveTag HasTag GetTags",
    "Name Parent ClassName Archivable",
    "Changed ChildAdded ChildRemoved DescendantAdded DescendantRemoving AncestryChanged AttributeChanged Destroying"],
  PVInstance: ["Instance", "GetPivot PivotTo", "", ""],
  Model: ["PVInstance", "GetBoundingBox GetExtentsSize MoveTo SetPrimaryPartCFrame TranslateBy ScaleTo GetScale", "PrimaryPart WorldPivot", ""],
  BasePart: ["PVInstance",
    "ApplyImpulse ApplyAngularImpulse CanCollideWith GetConnectedParts GetMass GetNetworkOwner GetTouchingParts GetVelocityAtPosition IsGrounded SetNetworkOwner BreakJoints Resize",
    "Anchored CanCollide CanQuery CanTouch CastShadow CFrame Color Material Mass Massless Orientation Position Reflectance Rotation Size Transparency AssemblyLinearVelocity AssemblyAngularVelocity BrickColor CollisionGroup Locked",
    "Touched TouchEnded"],
  Part: ["BasePart", "", "Shape", ""],
  MeshPart: ["BasePart", "", "MeshId TextureID", ""],
  Humanoid: ["Instance",
    "AddAccessory ApplyDescription ChangeState EquipTool GetState Move MoveTo TakeDamage UnequipTools",
    "Health MaxHealth WalkSpeed JumpPower JumpHeight HipHeight DisplayName RootPart Sit SeatPart MoveDirection FloorMaterial RigType",
    "Died HealthChanged Running Jumping Seated StateChanged"],
  Animator: ["Instance", "LoadAnimation GetPlayingAnimationTracks", "", ""],
  AnimationTrack: ["Instance", "Play Stop AdjustSpeed AdjustWeight GetMarkerReachedSignal", "Looped Length IsPlaying Priority Speed TimePosition", "Stopped Ended KeyframeReached"],
  Player: ["Instance",
    "Kick LoadCharacter GetMouse GetRankInGroup IsFriendsWith ClearCharacterAppearance GetJoinData",
    "Character DisplayName UserId Team TeamColor AccountAge Backpack PlayerGui",
    "CharacterAdded CharacterRemoving Chatted"],
  Players: ["Instance",
    "GetPlayers GetPlayerFromCharacter GetPlayerByUserId GetUserIdFromNameAsync GetNameFromUserIdAsync",
    "LocalPlayer MaxPlayers RespawnTime",
    "PlayerAdded PlayerRemoving"],
  Workspace: ["Model",
    "Raycast GetPartBoundsInBox GetPartBoundsInRadius GetPartsInPart Blockcast Spherecast",
    "CurrentCamera Gravity FallenPartsDestroyHeight Terrain StreamingEnabled DistributedGameTime",
    ""],
  Camera: ["Instance", "WorldToViewportPoint WorldToScreenPoint ViewportPointToRay ScreenPointToRay", "CFrame CameraType CameraSubject FieldOfView Focus ViewportSize", ""],
  RunService: ["Instance",
    "BindToRenderStep UnbindFromRenderStep IsClient IsServer IsStudio IsRunning IsEdit", "",
    "Heartbeat RenderStepped Stepped PreRender PreAnimation PreSimulation PostSimulation"],
  TweenService: ["Instance", "Create GetValue", "", ""],
  Tween: ["Instance", "Play Pause Cancel", "PlaybackState TweenInfo", "Completed"],
  UserInputService: ["Instance",
    "IsKeyDown IsMouseButtonPressed GetMouseLocation GetMouseDelta GetFocusedTextBox GetKeysPressed GetLastInputType",
    "TouchEnabled KeyboardEnabled MouseEnabled GamepadEnabled MouseBehavior MouseIconEnabled",
    "InputBegan InputChanged InputEnded TouchTap TouchStarted JumpRequest WindowFocused WindowFocusReleased"],
  ContextActionService: ["Instance", "BindAction UnbindAction GetAllBoundActionInfo", "", ""],
  CollectionService: ["Instance", "AddTag RemoveTag HasTag GetTagged GetTags GetInstanceAddedSignal GetInstanceRemovedSignal", "", ""],
  Debris: ["Instance", "AddItem", "", ""],
  HttpService: ["Instance", "GetAsync PostAsync RequestAsync JSONEncode JSONDecode GenerateGUID UrlEncode", "HttpEnabled", ""],
  DataStoreService: ["Instance", "GetDataStore GetOrderedDataStore GetGlobalDataStore", "", ""],
  DataStore: ["Instance", "GetAsync SetAsync UpdateAsync RemoveAsync IncrementAsync", "", ""],
  MarketplaceService: ["Instance",
    "PromptPurchase PromptProductPurchase PromptGamePassPurchase UserOwnsGamePassAsync GetProductInfo", "",
    "PromptPurchaseFinished PromptProductPurchaseFinished"],
  TeleportService: ["Instance", "Teleport TeleportAsync TeleportToPlaceInstance", "", "TeleportInitFailed"],
  Lighting: ["Instance", "GetMinutesAfterMidnight SetMinutesAfterMidnight", "Ambient Brightness ClockTime TimeOfDay FogEnd FogColor GlobalShadows", ""],
  SoundService: ["Instance", "PlayLocalSound", "AmbientReverb RespectFilteringEnabled", ""],
  ReplicatedStorage: ["Instance", "", "", ""],
  ServerStorage: ["Instance", "", "", ""],
  ServerScriptService: ["Instance", "", "", ""],
  StarterGui: ["Instance", "SetCore SetCoreGuiEnabled", "", ""],
  RemoteEvent: ["Instance", "FireServer FireClient FireAllClients", "", "OnServerEvent OnClientEvent"],
  RemoteFunction: ["Instance", "InvokeServer InvokeClient", "OnServerInvoke OnClientInvoke", ""],
  BindableEvent: ["Instance", "Fire", "", "Event"],
  BindableFunction: ["Instance", "Invoke", "OnInvoke", ""],
  Sound: ["Instance", "Play Stop Pause Resume", "SoundId Volume Looped Playing TimePosition TimeLength PlaybackSpeed IsPlaying", "Ended Played Stopped Loaded"],
  Tool: ["Instance", "", "Enabled CanBeDropped RequiresHandle Handle", "Equipped Unequipped Activated Deactivated"],
  ProximityPrompt: ["Instance", "InputHoldBegin InputHoldEnd", "ActionText ObjectText HoldDuration MaxActivationDistance Enabled KeyboardKeyCode RequiresLineOfSight", "Triggered TriggerEnded PromptShown PromptHidden"],
  GuiObject: ["Instance", "TweenPosition TweenSize TweenSizeAndPosition",
    "Position Size AnchorPoint BackgroundColor3 BackgroundTransparency BorderSizePixel Visible ZIndex LayoutOrder Rotation ClipsDescendants Active AbsolutePosition AbsoluteSize",
    "MouseEnter MouseLeave InputBegan InputEnded InputChanged"],
  Frame: ["GuiObject", "", "", ""],
  TextLabel: ["GuiObject", "", "Text TextColor3 TextSize Font TextScaled TextWrapped TextXAlignment TextYAlignment RichText", ""],
  TextButton: ["TextLabel", "", "", "Activated MouseButton1Click MouseButton1Down MouseButton1Up MouseButton2Click"],
  TextBox: ["TextLabel", "CaptureFocus ReleaseFocus", "PlaceholderText ClearTextOnFocus", "FocusLost Focused"],
  ImageLabel: ["GuiObject", "", "Image ImageColor3 ImageTransparency ScaleType", ""],
  ImageButton: ["ImageLabel", "", "", "Activated MouseButton1Click MouseButton1Down MouseButton1Up"],
  ScreenGui: ["Instance", "", "Enabled ResetOnSpawn DisplayOrder IgnoreGuiInset", ""],
  Script: ["Instance", "", "Disabled Enabled RunContext", ""],
  LocalScript: ["Script", "", "", ""],
  ModuleScript: ["Instance", "", "", ""],
  Folder: ["Instance", "", "", ""],
  WeldConstraint: ["Instance", "", "Part0 Part1 Enabled", ""],
  Attachment: ["Instance", "", "Position CFrame WorldPosition WorldCFrame Axis SecondaryAxis", ""],
  StringValue: ["Instance", "", "Value", ""],
  IntValue: ["Instance", "", "Value", ""],
  NumberValue: ["Instance", "", "Value", ""],
  BoolValue: ["Instance", "", "Value", ""],
  ObjectValue: ["Instance", "", "Value", ""],
  RaycastResult: [null, "", "Instance Position Normal Material Distance", ""],
  RBXScriptSignal: [null, "Connect Once Wait ConnectParallel", "", ""],
  RBXScriptConnection: [null, "Disconnect", "Connected", ""],
  DataModel: ["Instance", "GetService IsLoaded BindToClose", "PlaceId GameId JobId Workspace", "Loaded"],
};

// Services reachable through game:GetService("Name") and game.Name.
export const SERVICES = (
  "Workspace Players Lighting ReplicatedStorage ReplicatedFirst ServerStorage ServerScriptService StarterGui StarterPack StarterPlayer SoundService Teams " +
  "RunService TweenService UserInputService ContextActionService CollectionService Debris HttpService DataStoreService MarketplaceService TeleportService " +
  "PhysicsService MessagingService TextService GuiService ProximityPromptService BadgeService GroupService PathfindingService LocalizationService " +
  "TextChatService Chat PolicyService MemoryStoreService SocialService HapticService MaterialService AssetService ContentProvider"
).split(" ");

// Classes that can be created with Instance.new("Name").
export const CREATABLE = (
  "Part MeshPart WedgePart SpawnLocation Model Folder Humanoid Script LocalScript ModuleScript RemoteEvent RemoteFunction BindableEvent BindableFunction " +
  "StringValue IntValue NumberValue BoolValue ObjectValue Color3Value Vector3Value CFrameValue Attachment Weld WeldConstraint Motor6D Sound Animation Tool " +
  "Highlight PointLight SpotLight SurfaceLight ParticleEmitter Beam Trail Fire Smoke Sparkles BillboardGui SurfaceGui ScreenGui Frame TextLabel TextButton " +
  "TextBox ImageLabel ImageButton ScrollingFrame UIListLayout UIGridLayout UIPadding UICorner UIStroke UIScale UIAspectRatioConstraint ProximityPrompt " +
  "ClickDetector Decal Texture LinearVelocity AlignPosition AlignOrientation HingeConstraint SpringConstraint RopeConstraint BallSocketConstraint"
).split(" ");

// Roblox datatypes: constructors/statics ("static") and instance members.
export const DATATYPES = {
  Vector3: { statics: "new zero one xAxis yAxis zAxis FromNormalId FromAxis", props: "X Y Z Magnitude Unit", methods: "Cross Dot Lerp Angle FuzzyEq Max Min Floor Ceil Abs Sign" },
  Vector2: { statics: "new zero one xAxis yAxis", props: "X Y Magnitude Unit", methods: "Cross Dot Lerp Angle Max Min Floor Ceil Abs Sign" },
  CFrame: {
    statics: "new Angles lookAt lookAlong fromEulerAnglesXYZ fromEulerAnglesYXZ fromOrientation fromAxisAngle identity fromMatrix",
    props: "Position X Y Z LookVector RightVector UpVector Rotation",
    methods: "Inverse Lerp ToWorldSpace ToObjectSpace PointToWorldSpace PointToObjectSpace VectorToWorldSpace VectorToObjectSpace ToEulerAnglesXYZ ToEulerAnglesYXZ ToOrientation GetComponents Orthonormalize FuzzyEq",
  },
  Color3: { statics: "new fromRGB fromHSV fromHex", props: "R G B", methods: "Lerp ToHSV ToHex" },
  UDim2: { statics: "new fromScale fromOffset", props: "X Y Width Height", methods: "Lerp" },
  UDim: { statics: "new", props: "Scale Offset", methods: "" },
  BrickColor: { statics: "new random palette", props: "Name Number Color r g b", methods: "" },
  TweenInfo: { statics: "new", props: "Time EasingStyle EasingDirection RepeatCount Reverses DelayTime", methods: "" },
  RaycastParams: { statics: "new", props: "FilterDescendantsInstances FilterType IgnoreWater CollisionGroup RespectCanCollide", methods: "" },
  Random: { statics: "new", props: "", methods: "NextInteger NextNumber NextUnitVector Shuffle Clone" },
  Instance: { statics: "new fromExisting", props: "", methods: "" },
  DateTime: { statics: "now fromUnixTimestamp fromUnixTimestampMillis fromIsoDate", props: "UnixTimestamp UnixTimestampMillis", methods: "ToIsoDate FormatLocalTime FormatUniversalTime" },
};

// Enum categories and a selection of their items.
const letters = "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z";
export const ENUMS = {
  Material: "Plastic SmoothPlastic Neon Metal Wood WoodPlanks Brick Concrete Glass Grass Sand Ice Marble Granite Slate ForceField Foil Fabric Cobblestone Pebble DiamondPlate CorrodedMetal",
  KeyCode: letters + " Zero One Two Three Four Five Six Seven Eight Nine Space Return Tab Escape Backspace LeftShift RightShift LeftControl RightControl LeftAlt RightAlt Up Down Left Right Unknown",
  EasingStyle: "Linear Sine Back Quad Quart Quint Bounce Elastic Exponential Circular Cubic",
  EasingDirection: "In Out InOut",
  PartType: "Ball Block Cylinder Wedge CornerWedge",
  UserInputType: "MouseButton1 MouseButton2 MouseButton3 MouseWheel MouseMovement Touch Keyboard Gamepad1 Focus",
  UserInputState: "Begin Change End Cancel None",
  HumanoidStateType: "Running Jumping Freefall Dead Climbing Swimming Seated Landed Physics GettingUp FallingDown Flying PlatformStanding",
  RunContext: "Legacy Server Client Plugin",
  RaycastFilterType: "Exclude Include",
  NormalId: "Top Bottom Front Back Left Right",
  SortOrder: "LayoutOrder Name Custom",
  FillDirection: "Horizontal Vertical",
  TextXAlignment: "Left Center Right",
  TextYAlignment: "Top Center Bottom",
  CameraType: "Fixed Watch Attach Track Follow Custom Scriptable Orbital",
  MouseBehavior: "Default LockCenter LockCurrentPosition",
  AnimationPriority: "Idle Movement Action Core Action2 Action3 Action4",
  PlaybackState: "Begin Delayed Playing Paused Completed Cancelled",
  ZIndexBehavior: "Global Sibling",
  ScaleType: "Stretch Slice Tile Fit Crop",
};

// Types of well-known properties and methods, used for chained completion (Players.LocalPlayer.Character...).
export const MEMBER_TYPES = {
  "Players.LocalPlayer": "Player",
  "Player.Character": "Model",
  "Player.Backpack": "Instance",
  "Workspace.CurrentCamera": "Camera",
  "Model.PrimaryPart": "BasePart",
  "Humanoid.RootPart": "BasePart",
  "RemoteEvent.OnServerEvent": "RBXScriptSignal",
  "RemoteEvent.OnClientEvent": "RBXScriptSignal",
  "Instance.Changed": "RBXScriptSignal",
  "Instance.ChildAdded": "RBXScriptSignal",
  "Instance.ChildRemoved": "RBXScriptSignal",
  "Players.PlayerAdded": "RBXScriptSignal",
  "Players.PlayerRemoving": "RBXScriptSignal",
  "RunService.Heartbeat": "RBXScriptSignal",
  "RunService.RenderStepped": "RBXScriptSignal",
  "RunService.Stepped": "RBXScriptSignal",
  "BasePart.Touched": "RBXScriptSignal",
  "Humanoid.Died": "RBXScriptSignal",
  "Tween.Completed": "RBXScriptSignal",
  "Player.CharacterAdded": "RBXScriptSignal",
};

// Methods that return the same kind of object as their receiver or a fixed class.
export const METHOD_RETURNS = {
  Clone: "$self",
  FindFirstChild: "Instance",
  WaitForChild: "Instance",
  FindFirstAncestor: "Instance",
  FindFirstDescendant: "Instance",
  Connect: "RBXScriptConnection",
  Once: "RBXScriptConnection",
  Create: "Tween",
  GetPlayers: null,
};

// Methods whose first string argument names the returned class.
export const CLASS_FROM_ARGUMENT = new Set([
  "GetService", "FindFirstChildOfClass", "FindFirstChildWhichIsA", "FindFirstAncestorOfClass", "FindFirstAncestorWhichIsA",
]);
