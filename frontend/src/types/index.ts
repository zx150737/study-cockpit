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
  id: number | string;
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

// ---------------- 后台管理 ----------------

export interface AdminUserVO {
  userId: string;
  email: string;
  nickname: string;
  createdAt: string | null;
  lastSignIn: string | null;
  goalCount: number;
  taskTotal: number;
  taskFinished: number;
  overdueCount: number;
  completionRate: number;
  imageCount: number;
  archivedCount: number;
  weekCount: number;
}

export interface AdminImageVO {
  id: number;
  taskId: number;
  originalName: string;
  path: string;
  mimeType: string;
  fileSize: number;
  uploadTime: string;
  expireTime: string;
  archived: boolean;
  archivedAt: string | null;
  taskTitle: string | null;
}

export interface AdminEntryVO {
  userId: string;
  email: string;
  note: string | null;
  createdAt: string;
}

// ---------------- AI 学习助手 ----------------

export interface TutorStep {
  title: string;
  detail: string;
}

export interface TutorPractice {
  stem: string;
  options: string[];
  answer: string;
  explain: string;
}

export interface TutorReply {
  title?: string;
  answer: string;
  steps: TutorStep[];
  keyPoints: string[];
  pitfalls: string[];
  practice: TutorPractice[];
}

export interface TutorThreadVO {
  id: number;
  title: string;
  mode: 'explain' | 'practice';
  subjectId: number | null;
  subjectName: string | null;
  question: string;
  questionPreview?: string;
  reply: TutorReply | null;
  hasReply?: boolean;
  createdAt: string;
  repliedAt: string | null;
}

export interface TutorCardVO {
  id: number;
  threadId: number | null;
  front: string;
  back: string;
  subjectName: string | null;
  box: number;
  dueDate: string;
  reviewCount: number;
  correctCount: number;
  lastReviewedAt: string | null;
  overdue: boolean;
}

export interface TutorStatsVO {
  threadTotal: number;
  threadsToday: number;
  cardTotal: number;
  dueTotal: number;
  mastered: number;
  reviewedToday: number;
  accuracy: number;
}

export interface TutorThreadPage {
  list: TutorThreadVO[];
  total: number;
  page: number;
  size: number;
}

export interface TutorCardPage {
  list: TutorCardVO[];
  total: number;
  dueTotal: number;
  page: number;
  size: number;
}
