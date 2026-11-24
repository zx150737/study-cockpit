// 与后端 DTO 对齐的类型定义（跨边界类型安全）

export interface Result<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp: number;
}

export interface PageData<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
}

export interface GoalVO {
  id: number;
  name: string;
  completionRate: number;
  subjectCount: number;
  createdAt?: string;
}

export interface SubjectVO {
  id: number;
  goalId: number;
  name: string;
  defaultWeight: number;
  completionRate: number;
}

export interface TaskVO {
  id: number;
  goalId: number;
  goalName: string;
  subjectId: number;
  subjectName: string;
  title: string;
  weight: number;
  taskDate: string;
  deadline?: string;
  mastery?: number;
  reviewNote?: string;
  finishStatus: number;
  finishedAt?: string;
  overdue: boolean;
  imageCount: number;
  createdAt?: string;
}

export interface TaskReq {
  goalId: number;
  subjectId: number;
  title: string;
  weight?: number;
  taskDate: string;
  deadline?: string;
  mastery?: number;
  reviewNote?: string;
}

export interface TaskQuery {
  taskDate?: string;
  weekStart?: string;
  goalId?: number;
  subjectId?: number;
  finishStatus?: number;
  overdue?: boolean;
  page?: number;
  size?: number;
}

export interface ImageVO {
  id: number;
  taskId: number;
  originalName: string;
  temporaryUrl: string;
  uploadTime: string;
  expireTime: string;
  fileSize: number;
  mimeType: string;
}

export interface TrendPoint {
  date: string;
  rate: number | null;
  hasTask: boolean;
}

export interface GoalCard {
  goalId: number;
  name: string;
  completionRate: number;
  weekRate: number;
  taskTotal: number;
}

export interface BacklogItem {
  taskId: number;
  title: string;
  goalName: string;
  subjectName: string;
  taskDate: string;
  deadline?: string;
  overdueDays: number;
}

export interface Baseline {
  available: boolean;
  baselineRate: number | null;
  currentRate: number;
  diff: number | null;
  improvementPct: number | null;
  sampleWeeks: number;
}

export interface OverviewVO {
  todayRate: number;
  hasTask: boolean;
  weeklyTrend: TrendPoint[];
  goalCards: GoalCard[];
  streakDays: number;
  backlog: { total: number; items: BacklogItem[] };
  weeklyAvgRate: number;
  baseline: Baseline;
}

export interface SubjectRate {
  subjectId: number;
  name: string;
  completionRate: number;
}

export interface WeeklyVO {
  weekStart: string;
  weekEnd: string;
  completionRate: number;
  avgDailyRate: number;
  taskTotal: number;
  dailyTrend: TrendPoint[];
  subjectRates: SubjectRate[];
}

export interface WeekVO {
  id: number;
  weekStart: string;
  weekEnd: string;
  completionRate: number;
  avgDailyRate: number;
  checkinDays: number;
  taskTotal: number;
  taskFinished: number;
  snapshotAt?: string;
}

export interface WeekDetailVO {
  summary: WeekVO | null;
  tasks: TaskVO[];
}

export interface OverdueVO {
  total: number;
  items: BacklogItem[];
}
