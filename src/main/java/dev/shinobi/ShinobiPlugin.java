package dev.shinobi;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShinobiPlugin extends JavaPlugin {
    private ProfileManager profiles;
    private ChakraManager chakra;
    private JutsuRegistry jutsus;
    private JutsuService casting;
    private SealManager seals;
    private DojutsuManager dojutsu;
    private GateManager gates;
    private BondManager bonds;
    private MangekyoManager mangekyo;
    private ItemManager items;
    private MenuGui menu;
    private Hud hud;
    private MissionManager missions;
    private TrainingManager training;
    private SusanooManager susanoo;
    private CloneManager clones;
    private StationManager stations;
    private TempBlocks tempBlocks;
    private ScrollDeskGui deskGui;
    private MissionBoardGui boardGui;
    private SealingScrollGui sealingGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        profiles = new ProfileManager(this);
        hud = new Hud(this);
        chakra = new ChakraManager(this);
        jutsus = new JutsuRegistry();
        Jutsus.registerAll(this, jutsus);
        casting = new JutsuService(this);
        seals = new SealManager(this);
        dojutsu = new DojutsuManager(this);
        gates = new GateManager(this);
        bonds = new BondManager(this);
        mangekyo = new MangekyoManager(this);
        items = new ItemManager(this);
        menu = new MenuGui(this);
        missions = new MissionManager(this);
        training = new TrainingManager(this);
        susanoo = new SusanooManager(this);
        clones = new CloneManager(this);
        stations = new StationManager(this);
        tempBlocks = new TempBlocks(this);
        deskGui = new ScrollDeskGui(this);
        boardGui = new MissionBoardGui(this);
        sealingGui = new SealingScrollGui(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ShinobiListener(this), this);
        pm.registerEvents(seals, this);
        pm.registerEvents(items, this);
        pm.registerEvents(menu, this);
        pm.registerEvents(missions, this);
        pm.registerEvents(clones, this);
        pm.registerEvents(stations, this);
        pm.registerEvents(tempBlocks, this);
        pm.registerEvents(deskGui, this);
        pm.registerEvents(boardGui, this);
        pm.registerEvents(sealingGui, this);

        ShinobiCommands cmds = new ShinobiCommands(this);
        for (String name : new String[]{"shinobi", "bind", "jutsu", "bond", "dojutsu", "gate", "mission"}) {
            var c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }

        chakra.start();
        training.start();
        susanoo.start();
        // Autosave every 5 minutes.
        getServer().getScheduler().runTaskTimer(this, () -> profiles.saveAll(), 6000L, 6000L);

        // Support /reload: make sure online players have profiles.
        for (Player p : getServer().getOnlinePlayers()) {
            profiles.get(p);
            p.discoverRecipes(items.recipeKeys());
        }
        getLogger().info("ShinobiSMP enabled with " + jutsus.all().size() + " techniques.");
    }

    @Override
    public void onDisable() {
        if (susanoo != null) susanoo.endAll();
        if (clones != null) clones.removeAll();
        if (tempBlocks != null) tempBlocks.revertAll();
        if (profiles != null) profiles.saveAll();
        if (mangekyo != null) mangekyo.save();
        if (stations != null) stations.save();
        if (items != null) items.unregister();
    }

    public ProfileManager profiles() { return profiles; }
    public ChakraManager chakra() { return chakra; }
    public JutsuRegistry jutsus() { return jutsus; }
    public JutsuService casting() { return casting; }
    public SealManager seals() { return seals; }
    public DojutsuManager dojutsu() { return dojutsu; }
    public GateManager gates() { return gates; }
    public BondManager bonds() { return bonds; }
    public MangekyoManager mangekyo() { return mangekyo; }
    public ItemManager items() { return items; }
    public MenuGui menu() { return menu; }
    public Hud hud() { return hud; }
    public MissionManager missions() { return missions; }
    public TrainingManager training() { return training; }
    public SusanooManager susanoo() { return susanoo; }
    public CloneManager clones() { return clones; }
    public StationManager stations() { return stations; }
    public TempBlocks tempBlocks() { return tempBlocks; }
    public ScrollDeskGui deskGui() { return deskGui; }
    public MissionBoardGui boardGui() { return boardGui; }
    public SealingScrollGui sealingGui() { return sealingGui; }
}
