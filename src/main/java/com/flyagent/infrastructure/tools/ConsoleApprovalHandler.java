package com.flyagent.infrastructure.tools;

import com.flyagent.domain.tool.ApprovalHandler;
import com.flyagent.domain.tool.ApprovalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;

/**
 * 控制台交互式审批处理器。
 * <p>
 * 在控制台中展示审批请求详情并等待用户输入 Y/N 进行确认。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ConsoleApprovalHandler implements ApprovalHandler {
    private static final Logger log = LoggerFactory.getLogger(ConsoleApprovalHandler.class);

    /**
     * 在控制台中展示审批请求并等待用户确认。
     *
     * @param request 审批请求
     * @return true 如果用户输入 y 或 yes，否则 false
     */
    @Override
    public boolean approve(ApprovalRequest request) {
        System.out.println();
        System.out.println("=== Approval Required ===");
        System.out.println("Action: " + request.getActionType());
        System.out.println("Summary: " + request.getSummary());
        request.getDetails().forEach((k, v) ->
                System.out.println("  " + k + ": " + v));
        System.out.print("Approve? [y/N]: ");

        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine().trim().toLowerCase();
        boolean approved = "y".equals(line) || "yes".equals(line);
        log.info("Approval result: {} for action={}", approved, request.getActionType());
        return approved;
    }
}
