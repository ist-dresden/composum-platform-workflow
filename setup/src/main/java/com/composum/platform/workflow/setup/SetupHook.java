package com.composum.platform.workflow.setup;

import com.composum.sling.core.service.RepositorySetupService;
import com.composum.sling.core.setup.util.SetupUtil;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

@SuppressWarnings("Duplicates")
public class SetupHook implements InstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(SetupHook.class);

    private static final String SETUP_ACLS = "/conf/composum/platform/workflow/acl/setup.json";

    @Override
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    public void execute(InstallContext ctx) throws PackageException {
        switch (ctx.getPhase()) {
            case INSTALLED:
                LOG.info("installed: execute...");
                setupUsers(ctx);
                setupAcls(ctx);
                LOG.info("installed: execute ends.");
                break;
        }
    }

    protected void setupUsers(InstallContext ctx) throws PackageException {
        try {
            SetupUtil.setupGroupsAndUsers(ctx,
                    singletonMap("system/composum/platform/composum-platform-services",
                            emptyList()),
                    singletonMap("system/composum/platform/composum-platform-workflow-service",
                            singletonList("composum-platform-services")),
                    null);
        } catch (RuntimeException e) {
            LOG.error("" + e, e);
            throw new PackageException(e);
        }
    }

    protected void setupAcls(InstallContext ctx) throws PackageException {
        RepositorySetupService setupService = SetupUtil.getService(RepositorySetupService.class);
        try {
            Session session = ctx.getSession();
            setupService.addJsonAcl(session, SETUP_ACLS, null);
            session.save();
        } catch (Exception rex) {
            LOG.error(rex.getMessage(), rex);
            throw new PackageException(rex);
        }
    }
}
