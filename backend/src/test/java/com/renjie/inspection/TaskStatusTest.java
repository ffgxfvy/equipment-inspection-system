package com.renjie.inspection;

import com.renjie.inspection.entity.EquipmentStatus;
import com.renjie.inspection.entity.TaskPriority;
import com.renjie.inspection.entity.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机的单元测试。
 *
 * <p>这是本系统里最值得测试的一处纯逻辑：状态流转写错会导致
 * "已完成的任务被重新激活"这类数据不一致问题，且往往在演示时才被发现。
 * 把它做成不依赖 Spring 上下文的测试，运行只要几毫秒。
 */
class TaskStatusTest {

    @ParameterizedTest
    @DisplayName("合法流转")
    @CsvSource({
            "PENDING, DOING",
            "PENDING, DONE",
            "PENDING, CANCELLED",
            "DOING, DONE",
            "DOING, CANCELLED",
    })
    void shouldAllowValidTransitions(String from, String to) {
        assertTrue(TaskStatus.canTransfer(from, to), from + " → " + to + " 应该被允许");
    }

    @ParameterizedTest
    @DisplayName("非法流转")
    @CsvSource({
            "DONE, PENDING",
            "DONE, DOING",
            "CANCELLED, PENDING",
            "CANCELLED, DOING",
            "DOING, PENDING",
    })
    void shouldRejectInvalidTransitions(String from, String to) {
        assertFalse(TaskStatus.canTransfer(from, to), from + " → " + to + " 应该被拒绝");
    }

    @Test
    @DisplayName("终态不能再流转")
    void terminalStatesHaveNoOutgoingTransition() {
        for (String target : TaskStatus.ALL) {
            assertFalse(TaskStatus.canTransfer(TaskStatus.DONE, target));
            assertFalse(TaskStatus.canTransfer(TaskStatus.CANCELLED, target));
        }
    }

    @Test
    @DisplayName("流转到自身应被拒绝")
    void shouldRejectSelfTransition() {
        assertFalse(TaskStatus.canTransfer(TaskStatus.PENDING, TaskStatus.PENDING));
    }

    @Test
    @DisplayName("空值不应抛异常")
    void shouldTolerateNull() {
        assertFalse(TaskStatus.canTransfer(null, TaskStatus.DOING));
        assertFalse(TaskStatus.canTransfer(TaskStatus.PENDING, null));
    }

    @Test
    @DisplayName("未结束状态判定")
    void openStatuses() {
        assertTrue(TaskStatus.isOpen(TaskStatus.PENDING));
        assertTrue(TaskStatus.isOpen(TaskStatus.DOING));
        assertFalse(TaskStatus.isOpen(TaskStatus.DONE));
        assertFalse(TaskStatus.isOpen(TaskStatus.CANCELLED));
    }

    @Test
    @DisplayName("优先级排序权重：URGENT 最紧急")
    void priorityWeightOrdering() {
        assertTrue(TaskPriority.weight(TaskPriority.URGENT) > TaskPriority.weight(TaskPriority.HIGH));
        assertTrue(TaskPriority.weight(TaskPriority.HIGH) > TaskPriority.weight(TaskPriority.NORMAL));
        assertTrue(TaskPriority.weight(TaskPriority.NORMAL) > TaskPriority.weight(TaskPriority.LOW));
        // 传空值按 NORMAL 处理，避免排序时抛 NPE
        assertTrue(TaskPriority.weight(null) == TaskPriority.weight(TaskPriority.NORMAL));
    }

    @Test
    @DisplayName("需要关注的设备状态")
    void equipmentAttention() {
        assertTrue(EquipmentStatus.needsAttention(EquipmentStatus.FAULT));
        assertTrue(EquipmentStatus.needsAttention(EquipmentStatus.MAINTENANCE));
        assertFalse(EquipmentStatus.needsAttention(EquipmentStatus.RUNNING));
        assertFalse(EquipmentStatus.needsAttention(EquipmentStatus.STOPPED));
    }
}
