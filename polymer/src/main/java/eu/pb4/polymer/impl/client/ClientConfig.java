package eu.pb4.polymer.impl.client;

import eu.pb4.polymer.impl.PolymerImpl;

public class ClientConfig {
    public String _c1 = "Forces resource pack by default";
    public boolean forceResourcePackByDefault = false;
    public String _c2 = "Toggles Polymer's networking for client";
    public boolean enableNetworkSync = true;
    public String _c3 = "Enables alternative armor (texture) renderer. Always enabled with Iris/Canvas installed";
    public boolean useAlternativeArmorRenderer = false;
    public String _c4 = "Toggles visibility of F3 debug info";
    public boolean displayF3Info = true;
    public String _c5 = "Enables logging of invalid registry ids (BlockStates, Blocks, Items, etc) sent by server";
    public boolean logInvalidServerEntryIds = true;
    public String _c6 = "Disables Polymer's QoL changes that effects non-visual things";
    public boolean disableNonVisualQualityOfLifeChanges = PolymerImpl.DEV_ENV;
    public String _c7 = "Enables experimental support for less standard modded containers, allowing them to display polymer items";
    public boolean experimentalModdedContainerSupport = true;

    //public String _mod1 = "Enable (unstable/not fully working) REI compat. I suggest you to use EMI or JEI for better experience";
    //public boolean enableReiCompatibilityLayer = false;
}
