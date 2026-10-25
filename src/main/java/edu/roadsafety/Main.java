package edu.roadsafety;

import edu.roadsafety.service.RoadSafetySystem;
import edu.roadsafety.simulation.SimulationReportRunner;
import edu.roadsafety.ui.SimulationDashboard;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("--report")) {
            new SimulationReportRunner().runAll(System.out);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The Swing visualization requires a graphical desktop. Use '--report' for the dataset scenario reports.");
            return;
        }
        SwingUtilities.invokeLater(() -> new SimulationDashboard(new RoadSafetySystem()).setVisible(true));
    }
}
