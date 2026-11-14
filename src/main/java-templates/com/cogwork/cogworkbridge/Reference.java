package {{ package }};

/**
 * Build-time constants injected by Blossom from gradle.properties.
 */
public final class Reference {
    private Reference() {}

    public static final String MOD_ID = "{{ mod_id }}";
    public static final String MOD_NAME = "{{ mod_name }}";
    public static final String VERSION = "{{ mod_version }}";

    /** Name of the upstream jar whose assets we read at runtime. Never bundled. */
    public static final String ASSET_SOURCE_MOD_ID = "create";
}